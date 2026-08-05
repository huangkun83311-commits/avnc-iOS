package com.gaurav.avnc.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.json.JSONObject

class DeviceListAdapter(context: Context, devices: MutableList<JSONObject>) :
    ArrayAdapter<JSONObject>(context, 0, devices) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val device = getItem(position)!!
        val phoneId = device.optString("phone_id", device.optString("name", "未知"))
        val expire = device.optString("expire_time", "-")
        val codeId = device.optString("code_id", "")

        view.findViewById<TextView>(android.R.id.text1).text = "📱 $phoneId"
        view.findViewById<TextView>(android.R.id.text2).text =
            if (codeId.isNotEmpty()) "到期: $expire | 点击连接" else "到期: $expire"

        return view
    }
}
