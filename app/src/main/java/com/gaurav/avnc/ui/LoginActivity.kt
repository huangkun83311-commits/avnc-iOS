package com.gaurav.avnc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.R
import com.gaurav.avnc.viewmodel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private val viewModel by viewModels<LoginViewModel>()
    private val prefs by lazy { getSharedPreferences("avnc_login", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val accountInput = findViewById<EditText>(R.id.account_input)
        val passwordInput = findViewById<EditText>(R.id.password_input)
        val rememberCheck = findViewById<CheckBox>(R.id.remember_check)
        val loginBtn = findViewById<Button>(R.id.login_btn)

        // 自动登录
        val savedToken = prefs.getString("token", "")
        val savedUsername = prefs.getString("username", "")
        val savedIsAdmin = prefs.getBoolean("isAdmin", false)
        if (savedToken?.isNotEmpty() == true) {
            startHome(savedToken, savedUsername ?: "", savedIsAdmin)
            return
        }

        // 回填账号密码
        if (prefs.getBoolean("remember", false)) {
            accountInput.setText(prefs.getString("account", ""))
            passwordInput.setText(prefs.getString("password", ""))
            rememberCheck.isChecked = true
        }

        loginBtn.setOnClickListener {
            val account = accountInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.login(account, password)
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess { data ->
                try {
                    val success = data.optBoolean("success", false)
                    if (!success) {
                        Toast.makeText(this, data.optString("message", "登录失败"), Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    val token = data.optString("token", "")
                    if (token.isEmpty()) {
                        Toast.makeText(this, "登录失败: 未获取到token", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    val phone = data.optString("phone", "")
                    val isAdmin = phone.isEmpty()
                    val username: String
                    try {
                        username = if (isAdmin) {
                            data.getJSONObject("user").getString("username")
                        } else {
                            phone
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "解析用户信息失败: ${e.message}", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }

                    prefs.edit().putString("token", token)
                        .putString("username", username)
                        .putBoolean("isAdmin", isAdmin)
                        .putBoolean("remember", rememberCheck.isChecked)
                        .apply()

                    if (rememberCheck.isChecked) {
                        prefs.edit().putString("account", accountInput.text.toString().trim())
                            .putString("password", passwordInput.text.toString())
                            .apply()
                    } else {
                        prefs.edit().remove("account").remove("password").apply()
                    }

                    startHome(token, username, isAdmin)
                } catch (e: Exception) {
                    Toast.makeText(this, "处理数据异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { e ->
                Toast.makeText(this, "登录失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            loginBtn.isEnabled = !isLoading
            loginBtn.text = if (isLoading) "登录中..." else "登 录"
        }
    }

    private fun startHome(token: String, username: String, isAdmin: Boolean) {
        startActivity(Intent(this, MyDevicesActivity::class.java).apply {
            putExtra("token", token)
            putExtra("username", username)
            putExtra("is_admin", isAdmin)
        })
        finish()
    }
}
