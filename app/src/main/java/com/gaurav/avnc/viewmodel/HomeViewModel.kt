/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.LiveEvent
import com.gaurav.avnc.viewmodel.service.Discovery

class HomeViewModel(app: Application) : BaseViewModel(app) {

    /**
     * [ServerProfile]s stored in database.
     * Depending on the user pref, this list may be sorted by server name.
     */
    val serverProfiles by lazy {
        pref.ui.sortServerList.switchMap {
            if (it) serverProfileDao.getSortedLiveList()
            else serverProfileDao.getLiveList()
        }
    }

    /**
     * Used to find new servers.
     */
    val discovery = Discovery

    /**
     * Used for starting new VNC connections.
     */
    val newConnectionEvent = LiveEvent<ServerProfile>()

    /**
     * This event is used for editing/creating server profiles.
     * Home activity observes this event and starts profile editor when it is fired.
     */
    val editProfileEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a profile is saved to database.
     * Can be used to highlight the new profile in UI.
     */
    val profileSavedEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a profile is deleted from database.
     * This is used for notifying the user and potentially undo the deletion.
     */
    val profileDeletedEvent = LiveEvent<ServerProfile>()

    /**
     * Starts new connection to given profile.
     */
    fun startConnection(profile: ServerProfile) = newConnectionEvent.fire(profile)

    fun maybeConnectOnAppStart() = launchMain {
        serverProfileDao.getConnectableOnAppStart().firstOrNull()?.let { startConnection(it) }
    }

    /**************************************************************************
     * Server Discovery
     *
     * To save battery, Discovery is stopped when HomeActivity is in background.
     **************************************************************************/
    private var autoStopped = false

    fun startDiscovery() {
        autoStopped = false
        discovery.start(app)
    }

    fun stopDiscovery() {
        autoStopped = false
        discovery.stop()
    }

    fun autoStartDiscovery() {
        if (pref.server.discoveryAutorun || autoStopped)
            startDiscovery()
    }

    fun autoStopDiscovery() {
        if (discovery.isRunning.value == true) {
            stopDiscovery()
            autoStopped = true
        }
    }


    /**************************************************************************
     * Profile editing/creating
     *
     * These are invoked from UI on user actions. We simply fire [editProfileEvent]
     * with appropriate profile, causing the profile editor to be shown.
     *
     * NOTE: We need to make a copy of given profile because the instance
     * given to [editProfileEvent] can be modified by the editor.
     **************************************************************************/

    fun onNewProfile() = editProfileEvent.fire(ServerProfile())
    fun onNewProfile(source: ServerProfile) = editProfileEvent.fire(source.copy(ID = 0))
    fun onEditProfile(profile: ServerProfile) = editProfileEvent.fire(profile.copy())

    fun onDuplicateProfile(profile: ServerProfile) {
        val duplicate = profile.copy(ID = 0)
        duplicate.name += " (Copy)"
        editProfileEvent.fire(duplicate)
    }

    /**************************************************************************
     * Profile persistence
     *
     * These operations are asynchronous.
     **************************************************************************/

    fun saveProfile(profile: ServerProfile) = launchMain {
        serverProfileDao.save(profile)
        profileSavedEvent.fire(profile)
    }

    fun deleteProfile(profile: ServerProfile) = launchMain {
        serverProfileDao.delete(profile)
        profileDeletedEvent.fire(profile)
    }

    /**************************************************************************
     * Rediscovery Indicator
     *
     * [rediscoveredProfiles] is the intersection of saved & discovered servers.
     *
     * To detect reachable server in [serverProfiles], we could directly 'ping'
     * them, but that has its own issues.
     **************************************************************************/
    val rediscoveredProfiles by lazy {
        pref.server.rediscoveryIndicator.switchMap {
            if (it) prepareRediscoveredProfiles()
            else MutableLiveData(null)
        }
    }

    private fun prepareRediscoveredProfiles() = with(MediatorLiveData<List<ServerProfile>>()) {
        val filter = { saved: List<ServerProfile>?, discovered: List<ServerProfile>? ->
            saved?.filter { s -> discovered?.find { s.host == it.host && s.port == it.port } != null }
        }
        addSource(serverProfiles) { value = filter(it, discovery.servers.value) }
        addSource(discovery.servers) { value = filter(serverProfiles.value, it) }
        this
    }

    // ===== 以下是自定义追加：从服务器拉取设备列表 =====
    companion object {
        const val API_BASE = "http://106.52.57.127:5000"
    }

    var token: String = ""
    var username: String = ""
    var isAdmin: Boolean = false

    val myDeviceList = MutableLiveData<List<ServerProfile>>()
    val myIsLoading = MutableLiveData(false)
    val myErrorMessage = MutableLiveData<String>()

    fun fetchMyDevices() {
        myIsLoading.value = true
        launchMain {
            try {
                val url = if (isAdmin) "$API_BASE/api/admin/vnc_devices"
                          else "$API_BASE/api/client/devices"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = if (isAdmin) "GET" else "POST"
                if (isAdmin) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                } else {
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.write("{\"phone\":\"$username\"}".toByteArray())
                }
                val resp = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(resp)
                if (json.optBoolean("success")) {
                    val arr = json.getJSONArray("devices")
                    val list = mutableListOf<ServerProfile>()
                    for (i in 0 until arr.length()) {
                        val d = arr.getJSONObject(i)
                        list.add(ServerProfile(
                            name = d.getString("phone_id"),
                            host = "",
                            port = 5900
                        ))
                    }
                    myDeviceList.postValue(list)
                } else {
                    myErrorMessage.postValue(json.optString("message", "获取失败"))
                }
            } catch (e: Exception) {
                myErrorMessage.postValue("加载失败: ${e.message}")
            } finally {
                myIsLoading.postValue(false)
            }
        }
    }
}
