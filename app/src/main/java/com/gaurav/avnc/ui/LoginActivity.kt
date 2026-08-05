package com.gaurav.avnc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityLoginBinding
import com.gaurav.avnc.ui.home.HomeActivity
import com.gaurav.avnc.util.EdgeToEdgeHelper
import com.gaurav.avnc.viewmodel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EdgeToEdgeHelper.setDataBindingContentView(this, R.layout.activity_login) as ActivityLoginBinding

        binding.loginBtn.setOnClickListener {
            val account = binding.accountInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
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
            binding.loginBtn.isEnabled = !isLoading
            binding.loginBtn.text = if (isLoading) "登录中..." else "登 录"
        }
    }
}
