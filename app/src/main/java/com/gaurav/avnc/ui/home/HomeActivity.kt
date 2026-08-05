package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.LiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val deviceList = MutableLiveData<List<ServerProfile>>()
    val isLoading = MutableLiveData(false)
    val errorMessage = MutableLiveData<String>()
    val newConnectionEvent = LiveEvent<ServerProfile>()

    fun fetchDevices() {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val devices = withContext(Dispatchers.IO) {
                    val url = if (isAdmin) "$API_BASE/api/admin/vnc_devices" else "$API_BASE/api/client/devices"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = if (isAdmin) "GET" else "POST"
                    if (isAdmin) conn.setRequestProperty("Authorization", "Bearer $token")
                    else {
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        conn.outputStream.write(JSONObject().apply { put("phone", username) }.toString().toByteArray())
                    }
                    val resp = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(resp)
                    if (!json.optBoolean("success")) throw Exception(json.optString("message", "获取失败"))
                    val arr = json.getJSONArray("devices")
                    (0 until arr.length()).map { i ->
                        val d = arr.getJSONObject(i)
                        ServerProfile(name = d.getString("phone_id"), host = "", port = 5900)
                    }
                }
                deviceList.postValue(devices)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun connectDevice(phoneId: String, codeId: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("$API_BASE/api/validate").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.write(JSONObject().apply {
                        put("phone_id", phoneId)
                        put("code_id", codeId)
                        put("user_ip", "")
                    }.toString().toByteArray())
                    JSONObject(conn.inputStream.bufferedReader().readText())
                }
                if (result.getBoolean("success")) {
                    val host = result.getString("host")
                    val port = result.getInt("port")
                    newConnectionEvent.fire(ServerProfile(name = phoneId, host = host, port = port))
                } else {
                    errorMessage.postValue(result.optString("message", "验证失败"))
                }
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun startConnection(profile: ServerProfile) = newConnectionEvent.fire(profile)
}
