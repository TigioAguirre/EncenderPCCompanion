package com.example.bandcolorreact

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Tutorial guiado ventana a ventana: explica y ayuda a conceder, uno por
 * uno, cada permiso que EncenderPC companion necesita (heurística de
 * Nielsen "ayuda y documentación"), mostrando en todo momento en qué paso
 * está el usuario (dots + "Paso X de N", heurística "visibilidad del
 * estado del sistema") y permitiéndole retroceder u omitir libremente
 * (heurística "control y libertad del usuario").
 *
 * Se muestra automáticamente la primera vez que se abre la app
 * (ver MainActivity.onCreate) y también se puede volver a abrir en
 * cualquier momento desde el botón "Ver el tutorial otra vez".
 */
class TutorialActivity : AppCompatActivity() {

    private enum class StepType {
        WELCOME, NOTIFICATION_LISTENER, OVERLAY, BATTERY, POST_NOTIFICATIONS, URL_INPUT, DONE
    }

    private data class Step(
        val type: StepType,
        val iconRes: Int,
        val titleRes: Int,
        val descRes: Int
    )

    private val steps = listOf(
        Step(StepType.WELCOME, R.drawable.ic_large_encenderpc, R.string.tutorial_welcome_title, R.string.tutorial_welcome_desc),
        Step(StepType.NOTIFICATION_LISTENER, R.drawable.ic_bell_outline, R.string.tutorial_notification_title, R.string.tutorial_notification_desc),
        Step(StepType.OVERLAY, R.drawable.ic_layers_outline, R.string.tutorial_overlay_title, R.string.tutorial_overlay_desc),
        Step(StepType.BATTERY, R.drawable.ic_battery_outline, R.string.tutorial_battery_title, R.string.tutorial_battery_desc),
        Step(StepType.POST_NOTIFICATIONS, R.drawable.ic_shield_outline, R.string.tutorial_notifications_runtime_title, R.string.tutorial_notifications_runtime_desc),
        Step(StepType.URL_INPUT, R.drawable.ic_link_outline, R.string.tutorial_url_title, R.string.tutorial_url_desc),
        Step(StepType.DONE, R.drawable.ic_check_circle, R.string.tutorial_done_title, R.string.tutorial_done_desc)
    )

    private var currentStep = 0

    private lateinit var dotsContainer: LinearLayout
    private lateinit var textStepProgress: TextView
    private lateinit var imageStepIcon: ImageView
    private lateinit var textStepTitle: TextView
    private lateinit var textStepDesc: TextView
    private lateinit var chipStepStatus: View
    private lateinit var iconStepStatus: ImageView
    private lateinit var textStepStatus: TextView
    private lateinit var editStepUrl: EditText
    private lateinit var textStepUrlError: TextView
    private lateinit var btnStepAction: Button
    private lateinit var btnStepBack: Button
    private lateinit var btnStepSkip: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            renderStep() // el resultado (concedido o no) se refleja al instante
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        // Se marca como "visto" apenas se abre: el tutorial nunca vuelve a
        // interrumpir por su cuenta, aunque el usuario lo cierre a mitad de
        // camino. Siempre queda disponible manualmente desde MainActivity.
        AppPrefs.setTutorialShown(this, true)

        dotsContainer = findViewById(R.id.dotsContainer)
        textStepProgress = findViewById(R.id.textStepProgress)
        imageStepIcon = findViewById(R.id.imageStepIcon)
        textStepTitle = findViewById(R.id.textStepTitle)
        textStepDesc = findViewById(R.id.textStepDesc)
        chipStepStatus = findViewById(R.id.chipStepStatus)
        iconStepStatus = findViewById(R.id.iconStepStatus)
        textStepStatus = findViewById(R.id.textStepStatus)
        editStepUrl = findViewById(R.id.editStepUrl)
        textStepUrlError = findViewById(R.id.textStepUrlError)
        btnStepAction = findViewById(R.id.btnStepAction)
        btnStepBack = findViewById(R.id.btnStepBack)
        btnStepSkip = findViewById(R.id.btnStepSkip)

        buildDots()

        btnStepBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                renderStep()
            }
        }

        btnStepSkip.setOnClickListener { finish() }

        renderStep()
    }

    override fun onResume() {
        super.onResume()
        // Volvemos aquí después de ir a Ajustes (u otra app) a conceder un
        // permiso: refrescamos el paso actual para mostrar de inmediato si
        // ya quedó concedido.
        if (::btnStepAction.isInitialized) {
            renderStep()
        }
    }

    private fun buildDots() {
        dotsContainer.removeAllViews()
        steps.indices.forEach { index ->
            val dot = View(this)
            val params = LinearLayout.LayoutParams(0, 0).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.space_xs)
            }
            dot.layoutParams = params
            dot.tag = "dot_$index"
            dotsContainer.addView(dot)
        }
        updateDots()
    }

    private fun updateDots() {
        for (index in steps.indices) {
            val dot = dotsContainer.getChildAt(index) ?: continue
            val params = dot.layoutParams as LinearLayout.LayoutParams
            if (index == currentStep) {
                dot.setBackgroundResource(R.drawable.dot_active)
                params.width = resources.getDimensionPixelSize(R.dimen.space_lg)
                params.height = resources.getDimensionPixelSize(R.dimen.space_xs)
            } else {
                dot.setBackgroundResource(R.drawable.dot_inactive)
                params.width = resources.getDimensionPixelSize(R.dimen.space_xs) * 2
                params.height = resources.getDimensionPixelSize(R.dimen.space_xs) * 2
            }
            dot.layoutParams = params
        }
    }

    private fun renderStep() {
        val step = steps[currentStep]

        textStepProgress.text = getString(R.string.tutorial_step_of, currentStep + 1, steps.size)
        updateDots()

        imageStepIcon.setImageResource(step.iconRes)
        textStepTitle.text = getString(step.titleRes)
        textStepDesc.text = getString(step.descRes)

        chipStepStatus.visibility = View.GONE
        editStepUrl.visibility = View.GONE
        textStepUrlError.visibility = View.GONE
        btnStepBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        btnStepSkip.visibility = if (step.type == StepType.DONE) View.INVISIBLE else View.VISIBLE

        when (step.type) {
            StepType.WELCOME -> {
                btnStepAction.text = getString(R.string.tutorial_welcome_action)
                btnStepAction.setOnClickListener { goNext() }
            }

            StepType.NOTIFICATION_LISTENER -> renderPermissionStep(
                granted = isNotificationListenerEnabled(),
                onGrant = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )

            StepType.OVERLAY -> renderPermissionStep(
                granted = Settings.canDrawOverlays(this),
                onGrant = {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
            )

            StepType.BATTERY -> renderPermissionStep(
                granted = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName),
                onGrant = { requestIgnoreBatteryOptimizations() }
            )

            StepType.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    renderPermissionStep(
                        granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED,
                        onGrant = { notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                    )
                } else {
                    textStepDesc.text = getString(R.string.tutorial_notifications_runtime_not_needed)
                    btnStepAction.text = getString(R.string.tutorial_next)
                    btnStepAction.setOnClickListener { goNext() }
                }
            }

            StepType.URL_INPUT -> {
                editStepUrl.visibility = View.VISIBLE
                editStepUrl.setText(AppPrefs.getTriggerUrl(this))
                btnStepAction.text = getString(R.string.tutorial_next)
                btnStepAction.setOnClickListener {
                    val url = editStepUrl.text.toString().trim()
                    if (url.isNotEmpty() && !(url.startsWith("http://") || url.startsWith("https://"))) {
                        textStepUrlError.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    AppPrefs.setTriggerUrl(this, url)
                    goNext()
                }
            }

            StepType.DONE -> {
                btnStepAction.text = getString(R.string.tutorial_finish)
                btnStepAction.setOnClickListener { finish() }
            }
        }
    }

    /**
     * Dibuja el chip de estado (igual que en MainActivity, para
     * consistencia visual) y decide si el botón principal debe abrir el
     * ajuste correspondiente ("Conceder") o simplemente avanzar
     * ("Siguiente") porque el permiso ya está concedido.
     */
    private fun renderPermissionStep(granted: Boolean, onGrant: () -> Unit) {
        chipStepStatus.visibility = View.VISIBLE
        chipStepStatus.setBackgroundResource(if (granted) R.drawable.chip_ok else R.drawable.chip_warning)
        iconStepStatus.setImageResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_alert_circle)
        textStepStatus.text = getString(if (granted) R.string.status_chip_ok else R.string.status_chip_missing)
        textStepStatus.setTextColor(getColor(if (granted) R.color.state_ok else R.color.state_warning))

        if (granted) {
            btnStepAction.text = getString(R.string.tutorial_next)
            btnStepAction.setOnClickListener { goNext() }
        } else {
            btnStepAction.text = getString(R.string.btn_grant)
            btnStepAction.setOnClickListener { onGrant() }
        }
    }

    private fun goNext() {
        if (currentStep < steps.size - 1) {
            currentStep++
            renderStep()
        } else {
            finish()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(packageName)
    }
}
