package com.example.bandcolorreact.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.bandcolorreact.R
import com.example.bandcolorreact.auth.SessionManager
import com.example.bandcolorreact.data.DeviceRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class DevicesActivity : AppCompatActivity() {

    private val session = SessionManager()
    private val repository = DeviceRepository()
    private val adapter = DeviceAdapter()

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
}
