package com.example.bandcolorreact.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bandcolorreact.R
import com.example.bandcolorreact.data.DeviceRepository
import kotlinx.coroutines.launch

class AddDeviceActivity : AppCompatActivity() {

    private val repository = DeviceRepository()

    private lateinit var groupCreate: View
    private lateinit var groupCode: View
    private lateinit var etDeviceName: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvPairingCode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        groupCreate = findViewById(R.id.groupCreate)
        groupCode = findViewById(R.id.groupCode)
        etDeviceName = findViewById(R.id.etDeviceName)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        tvPairingCode = findViewById(R.id.tvPairingCode)

        findViewById<Button>(R.id.btnCreate).setOnClickListener { createDevice() }
        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }
    }

    private fun createDevice() {
        val name = etDeviceName.text.toString().trim()
        if (name.isEmpty()) {
            showError(getString(R.string.add_device_error_name_required))
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = repository.createDevice(name)
            setLoading(false)

            result
                .onSuccess { pairingCode -> showCode(pairingCode) }
                .onFailure { showError(it.localizedMessage ?: getString(R.string.add_device_error_generic)) }
        }
    }

    private fun showCode(code: String) {
        tvPairingCode.text = code
        groupCreate.visibility = View.GONE
        groupCode.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}
