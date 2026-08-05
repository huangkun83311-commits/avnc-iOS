package com.gaurav.avnc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gaurav.avnc.R
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.viewmodel.HomeViewModel
import org.json.JSONObject

class MyDevicesActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var groupAdapter: ArrayAdapter<String>
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private var allDevices = mutableListOf<JSONObject>()
    private var currentDevices = mutableListOf<JSONObject>()
    private var selectedGroupIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_devices)

        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        viewModel.token = intent.getStringExtra("token") ?: ""
        viewModel.username = intent.getStringExtra("username") ?: ""
        viewModel.isAdmin = intent.getBooleanExtra("is_admin", false)

        val groupList = findViewById<ListView>(R.id.group_list)
        val deviceList = findViewById<ListView>(R.id.device_list)

        findViewById<TextView>(R.id.title_text).text = "👤 ${viewModel.username} 的设备"

        groupAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("📁 全部设备", "📁 未分组"))
        groupList.adapter = groupAdapter

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        deviceList.adapter = deviceAdapter

        viewModel.myGroupList.observe(this) { groups ->
            groupAdapter.clear()
            groupAdapter.add("📁 全部设备")
            for (g in groups) groupAdapter.add("📁 ${g.optString("name", "未命名")}")
            groupAdapter.add("📁 未分组")
            groupAdapter.notifyDataSetChanged()
        }

        viewModel.myDeviceList.observe(this) { list ->
            allDevices.clear()
            list.forEach { allDevices.add(JSONObject().apply { put("name", it.name); put("code_id", it.host) }) }
            filterDevices()
        }

        viewModel.myErrorMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.newConnectionEvent.observe(this) { profile ->
            startVncActivity(this, profile)
        }

        groupList.setOnItemClickListener { _, _, pos, _ ->
            selectedGroupIndex = pos
            filterDevices()
        }

        groupList.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos <= 0) return@setOnItemLongClickListener true
            val item = groupAdapter.getItem(pos) ?: return@setOnItemLongClickListener true
            if (item == "📁 未分组") return@setOnItemLongClickListener true
            val groupName = item.removePrefix("📁 ")
            val group = viewModel.myGroupsData.find { it.optString("name") == groupName }
            val groupId = group?.optInt("id") ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle(groupName)
                .setItems(arrayOf("重命名", "删除")) { _, which ->
                    when (which) {
                        0 -> {
                            val input = EditText(this)
                            input.setText(groupName)
                            AlertDialog.Builder(this)
                                .setTitle("重命名分组")
                                .setView(input)
                                .setPositiveButton("确定") { _, _ ->
                                    viewModel.renameMyGroup(groupId, input.text.toString().trim())
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                        1 -> {
                            AlertDialog.Builder(this)
                                .setTitle("删除分组")
                                .setMessage("确定要删除「$groupName」吗？设备将移回未分组。")
                                .setPositiveButton("删除") { _, _ -> viewModel.deleteMyGroup(groupId) }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                }
                .show()
            true
        }

        deviceList.setOnItemClickListener { _, _, pos, _ ->
            val device = currentDevices.getOrNull(pos) ?: return@setOnItemClickListener
            viewModel.connectMyDevice(device.getString("name"), device.optString("code_id", ""))
        }

        deviceList.setOnItemLongClickListener { _, _, pos, _ ->
            val device = currentDevices.getOrNull(pos) ?: return@setOnItemLongClickListener true
            val phoneId = device.getString("name")
            val groups = viewModel.myGroupsData
            val items = mutableListOf("未分组")
            for (g in groups) items.add(g.optString("name", "未命名"))
            AlertDialog.Builder(this)
                .setTitle("移动到分组")
                .setItems(items.toTypedArray()) { _, which ->
                    val groupId = if (which == 0) null else groups[which - 1].optInt("id")
                    viewModel.moveDeviceToMyGroup(phoneId, groupId)
                }
                .show()
            true
        }

        findViewById<Button>(R.id.refresh_btn).setOnClickListener { viewModel.fetchMyDevices() }
        findViewById<Button>(R.id.add_group_btn).setOnClickListener {
            val input = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("新建分组")
                .setView(input)
                .setPositiveButton("确定") { _, _ -> viewModel.createMyGroup(input.text.toString().trim()) }
                .setNegativeButton("取消", null)
                .show()
        }
        findViewById<Button>(R.id.logout_btn).setOnClickListener {
            getSharedPreferences("avnc_login", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        viewModel.fetchMyDevices()

        // 8秒自动刷新
        val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    viewModel.fetchMyDevices()
                    refreshHandler.postDelayed(this, 8000)
                }
            }
        }
        refreshHandler.postDelayed(refreshRunnable, 8000)
        
    }

    private fun filterDevices() {
        val item = groupAdapter.getItem(selectedGroupIndex) ?: return
        currentDevices.clear()
        val groups = viewModel.myGroupsData
        val groupedIds = mutableSetOf<String>()
        for (g in groups) {
            val devs = g.optJSONArray("devices") ?: continue
            for (i in 0 until devs.length()) groupedIds.add(devs.getJSONObject(i).getString("phone_id"))
        }
        when {
            item == "📁 全部设备" -> currentDevices.addAll(allDevices)
            item == "📁 未分组" -> {
                for (d in allDevices) {
                    if (d.getString("name") !in groupedIds) currentDevices.add(d)
                }
            }
            else -> {
                val groupName = item.removePrefix("📁 ")
                val group = groups.find { it.optString("name") == groupName }
                val devs = group?.optJSONArray("devices") ?: return
                for (i in 0 until devs.length()) currentDevices.add(devs.getJSONObject(i))
            }
        }
        deviceAdapter.clear()
        currentDevices.forEach { deviceAdapter.add("📱 ${it.optString("phone_id", it.optString("name"))}") }
        deviceAdapter.notifyDataSetChanged()
    }
}
