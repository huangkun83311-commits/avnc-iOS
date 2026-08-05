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

    /**
     * 从服务器拉取设备列表
     */
    fun fetchDevices() {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val devices = withContext(Dispatchers.IO) {
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
                        if (isAdmin) {
                            arr = json.getJSONArray("devices")
                        } else {
                            arr = json.getJSONArray("devices")
                        }
                    } else {
                        throw Exception(json.optString("message", "获取设备列表失败"))
                    }

                    val list = mutableListOf<ServerProfile>()
                    for (i in 0 until arr.length()) {
                        val d = arr.getJSONObject(i)
                        val phoneId = if (isAdmin) d.getString("phone_id") else d.getString("phone_id")
                        val codeId = if (isAdmin) d.optString("code_id", "") else d.optString("code_id", "")
                        val expireTime = if (isAdmin) d.optString("expire_time", "-") else d.optString("expire_time", "-")

                        list.add(ServerProfile(
                            name = phoneId,
                            host = "",  // 连接时从 /api/validate 获取
                            port = 5900,
                            password = "",  // 连接时从 /api/validate 获取
                            useCount = 0
                        ).also {
                            // 把额外信息存到 host 字段里临时用
                            // 正式建议在 ServerProfile 里加字段
                        })
                    }
                    list
                }
                deviceList.postValue(devices)
            } catch (e: Exception) {
                errorMessage.postValue("加载失败: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
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
