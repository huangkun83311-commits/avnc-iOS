package com.gaurav.avnc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.R
import com.gaurav.avnc.ui.home.HomeActivity
import com.gaurav.avnc.viewmodel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private val viewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val accountInput = findViewById<EditText>(R.id.account_input)
        val passwordInput = findViewById<EditText>(R.id.password_input)
        val loginBtn = findViewById<Button>(R.id.login_btn)

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
                val token = data.getString("token")
                val username = if (data.has("phone")) {
                    data.getString("phone")
                } else {
                    data.getJSONObject("user").getString("username")
                }
                val isAdmin = data.has("user")

                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("token", token)
                    putExtra("username", username)
                    putExtra("is_admin", isAdmin)
                }
                startActivity(intent)
                finish()
            }.onFailure { e ->
                Toast.makeText(this, "登录失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            loginBtn.isEnabled = !isLoading
            loginBtn.text = if (isLoading) "登录中..." else "登 录"
        }
    }
}
