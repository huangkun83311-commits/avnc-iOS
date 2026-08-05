package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    val loginResult = MutableLiveData<Result<JSONObject>>()
    val isLoading = MutableLiveData(false)

    fun login(account: String, password: String) {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val isPhone = account.all { it.isDigit() } && account.length == 11
                    val url = if (isPhone) {
                        "http://106.52.57.127:5000/api/client/login"
                    } else {
                        "http://106.52.57.127:5000/api/login"
                    }
                    val jsonBody = if (isPhone) {
                        JSONObject().apply {
                            put("phone", account)
                            put("password", password)
                        }
                    } else {
                        JSONObject().apply {
                            put("username", account)
                            put("password", password)
                        }
                    }

                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.write(jsonBody.toString().toByteArray())

                    val response = conn.inputStream.bufferedReader().readText()
                    JSONObject(response)
                }
                loginResult.postValue(Result.success(result))
            } catch (e: Exception) {
                loginResult.postValue(Result.failure(e))
            } finally {
                isLoading.postValue(false)
            }
        }
    }
}
