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

    val serverProfiles by lazy {
        pref.ui.sortServerList.switchMap {
            if (it) serverProfileDao.getSortedLiveList()
            else serverProfileDao.getLiveList()
        }
    }

    val discovery = Discovery

    val newConnectionEvent = LiveEvent<ServerProfile>()
    val editProfileEvent = LiveEvent<ServerProfile>()
    val profileSavedEvent = LiveEvent<ServerProfile>()
    val profileDeletedEvent = LiveEvent<ServerProfile>()

    fun startConnection(profile: ServerProfile) = newConnectionEvent.fire(profile)

    fun maybeConnectOnAppStart() = launchMain {
        serverProfileDao.getConnectableOnAppStart().firstOrNull()?.let { startConnection(it) }
    }

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

    fun onNewProfile() = editProfileEvent.fire(ServerProfile())
    fun onNewProfile(source: ServerProfile) = editProfileEvent.fire(source.copy(ID = 0))
    fun onEditProfile(profile: ServerProfile) = editProfileEvent.fire(profile.copy())

    fun onDuplicateProfile(profile: ServerProfile) {
        val duplicate = profile.copy(ID = 0)
        duplicate.name += " (Copy)"
        editProfileEvent.fire(duplicate)
    }

    fun saveProfile(profile: ServerProfile) = launchMain {
        serverProfileDao.save(profile)
        profileSavedEvent.fire(profile)
    }

    fun deleteProfile(profile: ServerProfile) = launchMain {
        serverProfileDao.delete(profile)
        profileDeletedEvent.fire(profile)
    }

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

    // ===== 自定义追加 =====
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
                        list.add(ServerProfile(name = d.getString("phone_id"), host = "", port = 5900))
                    }
                    myDeviceList.postValue(list)
                } else {
                    myErrorMessage.postValue(json.optString("message", "获取失败"))
                }
            } catch (e: Exception) {
                val msg = e.message ?: "无详情"
                val stack = e.stackTraceToString()
                myErrorMessage.postValue("错误: $msg\n详情: ${stack.take(200)}")
            } finally {
                myIsLoading.postValue(false)
            }
        }
    }

    fun connectMyDevice(phoneId: String) {
        launchMain {
            try {
                val conn = java.net.URL("$API_BASE/api/validate").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write("{\"phone_id\":\"$phoneId\",\"code_id\":\"\",\"user_ip\":\"\"}".toByteArray())
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                if (json.getBoolean("success")) {
                    newConnectionEvent.fire(ServerProfile(name = phoneId, host = json.getString("host"), port = json.getInt("port")))
                } else {
                    myErrorMessage.postValue(json.optString("message", "验证失败"))
                }
            } catch (e: Exception) {
                myErrorMessage.postValue("连接失败: ${e.message}")
            }
        }
    }
}
