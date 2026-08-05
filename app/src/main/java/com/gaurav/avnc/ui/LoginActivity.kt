package com.gaurav.avnc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.databinding.ActivityLoginBinding
import com.gaurav.avnc.viewmodel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginBtn.setOnClickListener {
            val account = binding.accountInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.login(account, password)
        }

        // 观察登录结果
        viewModel.loginResult.observe(this) { result ->
            result.onSuccess { data ->
                val token = data.getString("token")
                val username = data.getString("phone") ?: data.getJSONObject("user").getString("username")
                val isAdmin = data.has("user")
                // 跳转到设备列表
                val intent = Intent(this, com.gaurav.avnc.ui.home.HomeActivity::class.java).apply {
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
            binding.loginBtn.isEnabled = !isLoading
            binding.loginBtn.text = if (isLoading) "登录中..." else "登 录"
        }
    }
}
