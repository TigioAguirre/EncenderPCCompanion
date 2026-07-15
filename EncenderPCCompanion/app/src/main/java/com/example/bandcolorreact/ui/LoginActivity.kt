package com.example.bandcolorreact.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bandcolorreact.R
import com.example.bandcolorreact.auth.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val session = SessionManager()

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya hay sesión activa, no tiene sentido mostrar el login.
        if (session.isLoggedIn) {
            goToDevices()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        findViewById<Button>(R.id.btnLogin).setOnClickListener { submit(isRegister = false) }
        findViewById<Button>(R.id.btnRegister).setOnClickListener { submit(isRegister = true) }
    }

    private fun submit(isRegister: Boolean) {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (email.isEmpty() || password.length < 6) {
            showError(getString(R.string.login_error_validation))
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = if (isRegister) {
                session.register(email, password)
            } else {
                session.login(email, password)
            }

            setLoading(false)

            result
                .onSuccess { goToDevices() }
                .onFailure { showError(it.localizedMessage ?: getString(R.string.login_error_generic)) }
        }
    }

    private fun goToDevices() {
        startActivity(Intent(this, DevicesActivity::class.java))
        finish()
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
