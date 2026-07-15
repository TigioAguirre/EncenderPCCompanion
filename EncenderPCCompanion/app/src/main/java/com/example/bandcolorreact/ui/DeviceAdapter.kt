package com.example.bandcolorreact.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bandcolorreact.R
import com.example.bandcolorreact.model.Device
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceAdapter(
    private var devices: List<Device> = emptyList()
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submitList(newDevices: List<Device>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_row, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvDeviceStatus)
        private val dot: android.view.View = itemView.findViewById(R.id.dotStatus)

        fun bind(device: Device) {
            tvName.text = device.name.ifBlank { itemView.context.getString(R.string.devices_unnamed) }

            dot.setBackgroundResource(
                if (device.isOnline) R.drawable.dot_online else R.drawable.dot_offline
            )

            val statusLabel = if (device.isOnline) {
                itemView.context.getString(R.string.devices_status_online)
            } else {
                val lastSeenText = if (device.lastSeenMillis > 0) {
                    timeFormat.format(Date(device.lastSeenMillis))
                } else null
                if (lastSeenText != null) {
                    itemView.context.getString(R.string.devices_status_offline_since, lastSeenText)
                } else {
                    itemView.context.getString(R.string.devices_status_offline)
                }
            }
            tvStatus.text = statusLabel
        }
    }
}
