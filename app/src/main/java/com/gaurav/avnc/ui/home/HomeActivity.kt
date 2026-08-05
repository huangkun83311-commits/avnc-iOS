package com.gaurav.avnc.ui.home

import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.gaurav.avnc.vnc.VncClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    val viewModel by viewModels<HomeViewModel>()
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setContentView(R.layout.activity_home)

        // 接收登录传过来的参数
        viewModel.token = intent.getStringExtra("token") ?: ""
        viewModel.username = intent.getStringExtra("username") ?: ""
        viewModel.isAdmin = intent.getBooleanExtra("is_admin", false)

        val titleText = findViewById<TextView>(R.id.title_text)
        val deviceList = findViewById<ListView>(R.id.device_list)
        val logoutBtn = findViewById<Button>(R.id.logout_btn)
        val refreshBtn = findViewById<Button>(R.id.refresh_btn)

        titleText.text = "👤 ${viewModel.username} 的设备"

        // 适配器
        val adapter = DeviceListAdapter(this, mutableListOf())
        deviceList.adapter = adapter

        // 观察设备列表
        viewModel.deviceList.observe(this) { devices ->
            adapter.clear()
            adapter.addAll(devices)
            adapter.notifyDataSetChanged()
        }

        // 观察加载状态
        viewModel.isLoading.observe(this) { loading ->
            refreshBtn.text = if (loading) "⏳ 加载中..." else "🔄 刷新"
            refreshBtn.isEnabled = !loading
        }

        // 观察错误
        viewModel.errorMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // 观察连接事件
        viewModel.newConnectionEvent.observe(this) { profile ->
            if (checkNativeLib()) startVncActivity(this, profile)
        }

        // 点击设备
        deviceList.setOnItemClickListener { _, _, position, _ ->
            val device = adapter.getItem(position) ?: return@setOnItemClickListener
            // 从设备名取 phone_id，code_id 暂时用空串（需从 API 存下来）
            viewModel.connectDevice(device.name, "")
        }

        // 刷新按钮
        refreshBtn.setOnClickListener { viewModel.fetchDevices() }

        // 退出登录
        logoutBtn.setOnClickListener {
            refreshJob?.cancel()
            finish()
        }

        // 首次加载
        viewModel.fetchDevices()

        // 8 秒自动刷新
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(8000)
                viewModel.fetchDevices()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshJob?.cancel()
    }

    private fun checkNativeLib(): Boolean {
        return runCatching { VncClient.loadLibrary() }.onFailure {
            MsgDialog.show(supportFragmentManager, "错误", "缺少原生库，请安装正确版本的 APK")
        }.isSuccess
    }
}
