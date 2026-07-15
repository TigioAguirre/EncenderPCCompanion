package com.example.bandcolorreact.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.bandcolorreact.R
import com.example.bandcolorreact.auth.SessionManager
import com.example.bandcolorreact.data.DeviceRepository
import com.example.bandcolorreact.model.Device
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class DevicesActivity : AppCompatActivity() {

    private val session = SessionManager()
    private val repository = DeviceRepository()
    private val adapter = DeviceAdapter(onDeleteClick = { device -> confirmDelete(device) })

    private var listenerRegistration: ListenerRegistration? = null

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_devices)

        // Registramos el token de FCM acá, con la sesión ya confirmada.
        // No alcanza con onNewToken() en PcStatusMessagingService: ese
        // método solo se dispara una vez, la primera vez que se genera
        // el token (normalmente antes del login), así que la llamada a
        // la Cloud Function fallaba por falta de autenticación y nunca
        // se reintentaba. Esto asegura que quede registrado apenas hay
        // un usuario logueado, cada vez que se entra a esta pantalla.
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                repository.registerFcmToken(token)
            }
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        findViewById<RecyclerView>(R.id.recyclerDevices).apply {
            layoutManager = LinearLayoutManager(this@DevicesActivity)
            adapter = this@DevicesActivity.adapter
        }

        findViewById<FloatingActionButton>(R.id.fabAddDevice).setOnClickListener {
            startActivity(Intent(this, AddDeviceActivity::class.java))
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            confirmLogout()
        }

        swipeRefresh.setOnRefreshListener {
            // El listener de Firestore ya es en tiempo real; esto es solo
            // feedback visual para quien tironea la lista hacia abajo.
            swipeRefresh.isRefreshing = false
        }
    }

    override fun onStart() {
        super.onStart()
        startListening()
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    private fun startListening() {
        listenerRegistration = repository.listenDevices(
            onChange = { devices ->
                adapter.submitList(devices)
                tvEmptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { error ->
                Toast.makeText(
                    this,
                    error.localizedMessage ?: getString(R.string.devices_error_loading),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun confirmDelete(device: Device) {
        AlertDialog.Builder(this)
            .setTitle(R.string.devices_delete_confirm_title)
            .setMessage(R.string.devices_delete_confirm_message)
            .setPositiveButton(R.string.devices_delete_confirm_yes) { _, _ -> deleteDevice(device) }
            .setNegativeButton(R.string.devices_delete_confirm_cancel, null)
            .show()
    }

    private fun deleteDevice(device: Device) {
        lifecycleScope.launch {
            // No hace falta actualizar la lista a mano acá: el listener de
            // Firestore (startListening) ya está escuchando en tiempo real
            // y va a sacar la fila apenas el documento desaparezca.
            repository.deleteDevice(device.id)
                .onFailure {
                    Toast.makeText(
                        this@DevicesActivity,
                        it.localizedMessage ?: getString(R.string.devices_delete_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.devices_logout_confirm_title)
            .setMessage(R.string.devices_logout_confirm_message)
            .setPositiveButton(R.string.devices_logout_action) { _, _ -> doLogout() }
            .setNegativeButton(R.string.devices_delete_confirm_cancel, null)
            .show()
    }

    private fun doLogout() {
        session.logout()
        // FLAG_ACTIVITY_NEW_TASK + CLEAR_TASK: vaciamos todo el back stack,
        // así que si el usuario después toca "atrás" desde LoginActivity no
        // vuelve a caer en esta pantalla con la sesión vieja todavía viva
        // en memoria (Activity ya destruida, pero el listener de Firestore
        // quedaría apuntando a datos del usuario anterior si no se limpia).
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
