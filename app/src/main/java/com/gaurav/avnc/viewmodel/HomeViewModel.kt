package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.LiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HomeViewModel(app: Application) : BaseViewModel(app) {

    companion object {
        const val API_BASE = "http://106.52.57.127:5000"
    }

    var token: String = ""
    var username: String = ""
    var isAdmin: Boolean = false

    /**
     * 设备列表（从服务器拉取）
     */
    val deviceList = MutableLiveData<List<ServerProfile>>()

    /**
     * 加载中状态
     */
    val isLoading = MutableLiveData(false)

    /**
     * 错误消息
     */
    val errorMessage = MutableLiveData<String>()

    /**
     * 用于启动 VNC 连接
     */
    val newConnectionEvent = LiveEvent<ServerProfile>()

    // ===== 分组相关 =====
    val groupList = MutableLiveData<List<JSONObject>>()
    var groupsData = mutableListOf<JSONObject>()

    val rawDeviceList = MutableLiveData<MutableList<JSONObject>>()
    private var rawDevices = mutableListOf<JSONObject>()

    /**
     * 从服务器拉取设备列表
     */
    fun fetchDevices() {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val (devices, rawList) = withContext(Dispatchers.IO) {
                    val url = if (isAdmin) {
                        "$API_BASE/api/admin/vnc_devices"
                    } else {
                        "$API_BASE/api/client/devices"
                    }

                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = if (isAdmin) "GET" else "POST"

                    if (isAdmin) {
                        conn.setRequestProperty("Authorization", "Bearer $token")
                    } else {
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        val body = JSONObject().apply { put("phone", username) }
                        conn.outputStream.write(body.toString().toByteArray())
                    }

                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    val arr: JSONArray
                    val success = json.optBoolean("success", false)
                    if (success) {
                        arr = if (isAdmin) json.getJSONArray("devices") else json.getJSONArray("devices")
                    } else {
                        throw Exception(json.optString("message", "获取设备列表失败"))
                    }

                    val list = mutableListOf<ServerProfile>()
                    val raw = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) {
                        val d = arr.getJSONObject(i)
                        val phoneId = d.getString("phone_id")
                        val codeId = d.optString("code_id", "")
                        val expireTime = d.optString("expire_time", "-")

                        list.add(ServerProfile(
                            name = phoneId,
                            host = "",
                            port = 5900,
                            password = "",
                            useCount = 0
                        ))
                        raw.add(d)
                    }
                    Pair(list, raw)
                }
                deviceList.postValue(devices)
                rawDevices = rawList
                rawDeviceList.postValue(rawDevices)
                fetchGroups()
            } catch (e: Exception) {
                errorMessage.postValue("加载失败: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    /**
     * 拉取分组列表
     */
    fun fetchGroups() {
        if (isAdmin) {
            groupList.postValue(emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) {
                    val conn = URL("$API_BASE/api/my/groups").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    val resp = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(resp)
                    if (json.getBoolean("success")) json.getJSONArray("groups") else JSONArray()
                }
                val list = mutableListOf<JSONObject>()
                for (i in 0 until groups.length()) list.add(groups.getJSONObject(i))
                groupsData = list
                groupList.postValue(list)
            } catch (_: Exception) {}
        }
    }

    /**
     * 验证设备并获取连接信息，然后启动连接
     */
    fun connectDevice(phoneId: String, codeId: String) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = "$API_BASE/api/validate"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    val body = JSONObject().apply {
                        put("phone_id", phoneId)
                        put("code_id", codeId)
                        put("user_ip", "")
                    }
                    conn.outputStream.write(body.toString().toByteArray())

                    val response = conn.inputStream.bufferedReader().readText()
                    JSONObject(response)
                }

                if (result.getBoolean("success")) {
                    val host = result.getString("host")
                    val port = result.getInt("port")

                    val profile = ServerProfile(
                        name = phoneId,
                        host = host,
                        port = port,
                        password = ""
                    )
                    newConnectionEvent.fire(profile)
                } else {
                    errorMessage.postValue("验证失败: ${result.optString("message", "未知错误")}")
                }
            } catch (e: Exception) {
                errorMessage.postValue("连接失败: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun startConnection(profile: ServerProfile) = newConnectionEvent.fire(profile)
}
