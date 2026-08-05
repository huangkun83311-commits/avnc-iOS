package com.gaurav.avnc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gaurav.avnc.R
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.viewmodel.HomeViewModel

class MyDevicesActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_devices)

        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        viewModel.token = intent.getStringExtra("token") ?: ""
        viewModel.username = intent.getStringExtra("username") ?: ""
        viewModel.isAdmin = intent.getBooleanExtra("is_admin", false)

        val deviceList = findViewById<ListView>(R.id.device_list)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        deviceList.adapter = adapter

        findViewById<TextView>(R.id.title_text).text = "👤 ${viewModel.username} 的设备"

        viewModel.myDeviceList.observe(this) { devices ->
            adapter.clear()
            devices.forEach { adapter.add("📱 ${it.name}") }
        }

        viewModel.myErrorMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        deviceList.setOnItemClickListener { _, _, position, _ ->
            val device = viewModel.myDeviceList.value?.getOrNull(position) ?: return@setOnItemClickListener
            viewModel.connectMyDevice(device.name)
        }

        viewModel.newConnectionEvent.observe(this) { profile ->
            startVncActivity(this, profile)
        }

        findViewById<Button>(R.id.refresh_btn).setOnClickListener {
            viewModel.fetchMyDevices()
        }

        findViewById<Button>(R.id.logout_btn).setOnClickListener {
            getSharedPreferences("avnc_login", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        viewModel.fetchMyDevices()
    }
}
