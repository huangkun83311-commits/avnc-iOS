package com.gaurav.avnc.ui.home

import android.os.Bundle
import android.view.Window
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.gaurav.avnc.vnc.VncClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class HomeActivity : AppCompatActivity() {
    val viewModel by viewModels<HomeViewModel>()
    private var refreshJob: Job? = null
    private lateinit var groupList: ListView
    private lateinit var deviceList: ListView
    private lateinit var deviceAdapter: DeviceListAdapter
    private var allDevices = mutableListOf<JSONObject>()
    private var currentGroupDevices = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setContentView(R.layout.activity_home)

        viewModel.token = intent.getStringExtra("token") ?: ""
        viewModel.username = intent.getStringExtra("username") ?: ""
        viewModel.isAdmin = intent.getBooleanExtra("is_admin", false)

        val titleText = findViewById<TextView>(R.id.title_text)
        groupList = findViewById(R.id.group_list)
        deviceList = findViewById(R.id.device_list)
        val logoutBtn = findViewById<Button>(R.id.logout_btn)
        val refreshBtn = findViewById<Button>(R.id.refresh_btn)

        titleText.text = "👤 ${viewModel.username} 的设备"

        // 分组列表适配器
        val groupAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf("📁 全部设备", "📁 未分组"))
        groupList.adapter = groupAdapter

        // 设备列表适配器
        deviceAdapter = DeviceListAdapter(this, currentGroupDevices)
        deviceList.adapter = deviceAdapter

        // 加载分组
        viewModel.groupList.observe(this) { groups ->
            groupAdapter.clear()
            groupAdapter.add("📁 全部设备")
            for (g in groups) {
                groupAdapter.add("📁 ${g.optString("name", "未命名")}")
            }
            groupAdapter.add("📁 未分组")
            groupAdapter.notifyDataSetChanged()
        }

        // 原始设备数据
        viewModel.rawDeviceList.observe(this) { devices ->
            allDevices = devices
            // 默认显示全部
            currentGroupDevices.clear()
            currentGroupDevices.addAll(allDevices)
            deviceAdapter.notifyDataSetChanged()
        }

        // 加载状态
        viewModel.isLoading.observe(this) { loading ->
            refreshBtn.text = if (loading) "⏳ 加载中..." else "🔄 刷新"
        }

        // 错误
        viewModel.errorMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // 连接事件
        viewModel.newConnectionEvent.observe(this) { profile ->
            if (checkNativeLib()) startVncActivity(this, profile)
        }

        // 点击分组
        groupList.setOnItemClickListener { _, _, position, _ ->
            val selected = groupAdapter.getItem(position) ?: return@setOnItemClickListener
            val groups = viewModel.groupsData

            currentGroupDevices.clear()
            when {
                selected == "📁 全部设备" -> currentGroupDevices.addAll(allDevices)
                selected == "📁 未分组" -> {
                    val groupedIds = mutableSetOf<String>()
                    for (g in groups) {
                        val devs = g.optJSONArray("devices") ?: continue
                        for (i in 0 until devs.length()) {
                            groupedIds.add(devs.getJSONObject(i).getString("phone_id"))
                        }
                    }
                    for (d in allDevices) {
                        if (d.getString("phone_id") !in groupedIds) currentGroupDevices.add(d)
                    }
                }
                else -> {
                    // 自定义分组
                    val groupName = selected.removePrefix("📁 ")
                    for (g in groups) {
                        if (g.optString("name") == groupName) {
                            val devs = g.optJSONArray("devices") ?: continue
                            for (i in 0 until devs.length()) {
                                currentGroupDevices.add(devs.getJSONObject(i))
                            }
                            break
                        }
                    }
                }
            }
            deviceAdapter.notifyDataSetChanged()
        }

        // 点击设备
        deviceList.setOnItemClickListener { _, _, position, _ ->
            val device = currentGroupDevices.getOrNull(position) ?: return@setOnItemClickListener
            val phoneId = device.optString("phone_id", device.optString("name", ""))
            viewModel.connectDevice(phoneId, device.optString("code_id", ""))
        }

        refreshBtn.setOnClickListener { viewModel.fetchDevices() }

        logoutBtn.setOnClickListener {
            refreshJob?.cancel()
            finish()
        }

        viewModel.fetchDevices()
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
            MsgDialog.show(supportFragmentManager, "错误", "缺少原生库")
        }.isSuccess
    }
}
