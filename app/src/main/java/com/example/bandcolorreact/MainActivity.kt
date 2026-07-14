package com.example.bandcolorreact

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    /**
     * Agrupa las vistas de una fila de estado (icono, título, chip
     * OK/Falta y botón "Conceder"). Cada fila viene de un mismo layout
     * reutilizado (item_status_row.xml) incluido 4 veces con <include>,
     * así que las vistas internas se buscan siempre a partir del
     * contenedor de esa fila concreta y nunca desde la Activity
     * directamente (evita resolver el id equivocado entre filas iguales).
     */
    private class StatusRow(container: View) {
        val icon: ImageView = container.findViewById(R.id.iconRow)
        val title: TextView = container.findViewById(R.id.textRowTitle)
        val chip: View = container.findViewById(R.id.chipRowStatus)
        val chipIcon: ImageView = container.findViewById(R.id.iconRowStatus)
        val chipText: TextView = container.findViewById(R.id.textRowStatus)
        val actionButton: Button = container.findViewById(R.id.btnRowAction)
    }

    private lateinit var switchServiceEnabled: SwitchCompat
    private lateinit var switchSubtitle: TextView
    private lateinit var textStatusSummary: TextView
    private lateinit var editTriggerUrl: EditText
    private lateinit var textUrlInlineError: TextView
    private lateinit var btnSaveUrl: Button

    private lateinit var rowNotification: StatusRow
    private lateinit var rowOverlay: StatusRow
    private lateinit var rowBattery: StatusRow
    private lateinit var rowPostNotifications: StatusRow

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lanzar tutorial automáticamente la primera vez
        if (!AppPrefs.isTutorialShown(this)) {
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        setContentView(R.layout.activity_main)

        switchServiceEnabled = findViewById(R.id.switchServiceEnabled)
        switchSubtitle = findViewById(R.id.switchSubtitle)
        textStatusSummary = findViewById(R.id.textStatusSummary)

        rowNotification = StatusRow(findViewById(R.id.rowNotification))
        rowOverlay = StatusRow(findViewById(R.id.rowOverlay))
        rowBattery = StatusRow(findViewById(R.id.rowBattery))
        rowPostNotifications = StatusRow(findViewById(R.id.rowPostNotifications))

        rowNotification.icon.setImageResource(R.drawable.ic_bell_outline)
        rowNotification.title.text = getString(R.string.tutorial_notification_title)
        rowNotification.actionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        rowOverlay.icon.setImageResource(R.drawable.ic_layers_outline)
        rowOverlay.title.text = getString(R.string.tutorial_overlay_title)
        rowOverlay.actionButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        rowBattery.icon.setImageResource(R.drawable.ic_battery_outline)
        rowBattery.title.text = getString(R.string.tutorial_battery_title)
        rowBattery.actionButton.setOnClickListener { requestIgnoreBatteryOptimizations() }

        rowPostNotifications.icon.setImageResource(R.drawable.ic_shield_outline)
        rowPostNotifications.title.text = getString(R.string.tutorial_notifications_runtime_title)
        rowPostNotifications.actionButton.setOnClickListener { requestNotificationPermission() }

        switchServiceEnabled.isChecked = AppPrefs.isServiceEnabled(this)
        updateSwitchSubtitle(switchServiceEnabled.isChecked)

        switchServiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.setServiceEnabled(this, isChecked)
            updateSwitchSubtitle(isChecked)
            val message = if (isChecked) getString(R.string.toast_service_enabled) else getString(R.string.toast_service_disabled)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnHelp).setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }
        findViewById<Button>(R.id.btnViewTutorialAgain).setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        editTriggerUrl = findViewById(R.id.editTriggerUrl)
        textUrlInlineError = findViewById(R.id.textUrlInlineError)
        editTriggerUrl.setText(AppPrefs.getTriggerUrl(this))

        // Prevención de errores: se valida mientras el usuario escribe, no
        // solo al pulsar "Guardar" (heurística 5 de Nielsen).
        editTriggerUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim().orEmpty()
                textUrlInlineError.visibility =
                    if (url.isNotEmpty() && !isValidUrl(url)) View.VISIBLE else View.GONE
            }
        })

        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        btnSaveUrl.setOnClickListener { saveTriggerUrl() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, getString(R.string.toast_battery_already_exempt), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try { startActivity(intent) }
        catch (e: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Toast.makeText(this, getString(R.string.toast_notification_permission_not_needed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    private fun saveTriggerUrl() {
        val url = editTriggerUrl.text.toString().trim()
        if (url.isNotEmpty() && !isValidUrl(url)) {
            textUrlInlineError.visibility = View.VISIBLE
            Toast.makeText(this, getString(R.string.toast_url_invalid), Toast.LENGTH_LONG).show()
            return
        }
        AppPrefs.setTriggerUrl(this, url)
        if (url.isEmpty()) Toast.makeText(this, getString(R.string.toast_url_empty), Toast.LENGTH_LONG).show()
        else Toast.makeText(this, getString(R.string.toast_url_saved), Toast.LENGTH_SHORT).show()
    }

    private fun updateSwitchSubtitle(enabled: Boolean) {
        switchSubtitle.text = if (enabled) getString(R.string.switch_subtitle_on) else getString(R.string.switch_subtitle_off)
    }

    /**
     * Refresca las 4 filas de estado y el resumen superior. Es la única
     * fuente de verdad de "qué falta": no hay ya un bloque de texto
     * separado que pueda quedar desincronizado con los botones, como
     * pasaba antes (heurística 1 - Visibilidad del estado del sistema).
     */
    private fun updateStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val notificationGranted = isNotificationListenerEnabled()
        val batteryExempt = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        renderRow(rowNotification, notificationGranted)
        renderRow(rowOverlay, overlayGranted)
        renderRow(rowBattery, batteryExempt)
        renderRow(rowPostNotifications, postNotificationsGranted)

        val grantedCount = listOf(notificationGranted, overlayGranted, batteryExempt, postNotificationsGranted).count { it }
        val total = 4
        textStatusSummary.text = if (grantedCount == total) {
            getString(R.string.main_status_all_ok)
        } else {
            getString(R.string.main_status_missing, total - grantedCount, total)
        }
    }

    private fun renderRow(row: StatusRow, granted: Boolean) {
        row.chip.setBackgroundResource(if (granted) R.drawable.chip_ok else R.drawable.chip_warning)
        row.chipIcon.setImageResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_alert_circle)
        row.chipText.text = getString(if (granted) R.string.status_chip_ok else R.string.status_chip_missing)
        row.chipText.setTextColor(getColor(if (granted) R.color.state_ok else R.color.state_warning))
        row.actionButton.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabledListeners.contains(packageName)
    }
}
