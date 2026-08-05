package com.gaurav.avnc.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.gaurav.avnc.model.ServerProfile

class DeviceListAdapter(context: Context, devices: MutableList<ServerProfile>) :
    ArrayAdapter<ServerProfile>(context, 0, devices) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val device = getItem(position)!!
        view.findViewById<TextView>(android.R.id.text1).text = "📱 ${device.name}"
        view.findViewById<TextView>(android.R.id.text2).text = "点击连接"

        return view
    }
}
