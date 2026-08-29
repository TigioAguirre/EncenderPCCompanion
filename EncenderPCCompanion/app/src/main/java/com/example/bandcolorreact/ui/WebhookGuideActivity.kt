package com.example.bandcolorreact.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bandcolorreact.R

/**
 * Guía completa, paso a paso, de cómo conseguir un webhook usable con
 * EncenderPC companion usando Alexa + VirtualSmartHome (el camino más
 * simple y gratuito): desde habilitar la skill de Alexa hasta crear la
 * rutina que realmente prenda la PC.
 *
 * Esta guía es solo informativa: no pide ni guarda el enlace directamente.
 * Pegar y guardar el webhook se sigue haciendo desde TutorialActivity
 * (paso "Tu enlace de encendido") o desde la pantalla principal
 * (MainActivity).
 *
 * Se abre desde:
 *  - El paso "Tu enlace de encendido" del tutorial principal
 *    (TutorialActivity).
 *  - El enlace "¿Cómo obtener mi webhook?" en la tarjeta de enlace de
 *    MainActivity.
 *
 * Sigue el mismo patrón de "un solo layout, se repinta por paso" que
 * TutorialActivity, para mantener consistencia visual y de código
 * (heurística 4 de Nielsen - Consistencia y estándares).
 */
class WebhookGuideActivity : AppCompatActivity() {

    private enum class GuideStepType { INTRO, SKILL, DEVICE, ROUTINE, DONE }

    /**
     * [imageRes] queda pensado para cuando se agreguen las capturas de
     * pantalla reales: basta con crear el drawable (por ejemplo
     * "wg_step_skill.png" en res/drawable) y pasarlo acá. Mientras
     * [imageRes] sea null, ese paso muestra el placeholder punteado.
     */
    private data class GuideStep(
        val type: GuideStepType,
        val iconRes: Int,
        val titleRes: Int,
        val descRes: Int,
        val actionLabelRes: Int? = null,
        val actionUrl: String? = null,
        val actionPackage: String? = null,
        val imageRes: List<Int> = emptyList()
    )

    private val steps = listOf(
        GuideStep(GuideStepType.INTRO, R.drawable.ic_large_encenderpc, R.string.wg_intro_title, R.string.wg_intro_desc,imageRes =listOf(R.drawable.guia1)),
        GuideStep(
            GuideStepType.SKILL, R.drawable.ic_shield_outline, R.string.wg_skill_title, R.string.wg_skill_desc,
            actionLabelRes = R.string.wg_skill_action, actionUrl = ALEXA_SKILL_URL,
            imageRes = listOf(R.drawable.rutina ,R.drawable.rutina2,R.drawable.rutina3)
        ),
        GuideStep(
            GuideStepType.DEVICE, R.drawable.ic_layers_outline, R.string.wg_device_title, R.string.wg_device_desc,
            actionLabelRes = R.string.wg_device_action, actionUrl = "https://www.virtualsmarthome.xyz/url_routine_trigger/",imageRes = listOf(R.drawable.hook1)

        ),
        GuideStep(
            GuideStepType.ROUTINE, R.drawable.ic_bell_outline, R.string.wg_routine_title, R.string.wg_routine_desc,
            actionLabelRes = R.string.wg_routine_action, actionPackage = "com.amazon.dee.app",imageRes = listOf(R.drawable.encender,R.drawable.encender2,R.drawable.encender3,R.drawable.encender4,R.drawable.encender5,R.drawable.encender6,R.drawable.encender7,R.drawable.encender8)

        ),
        GuideStep(GuideStepType.DONE, R.drawable.ic_check_circle, R.string.wg_done_title, R.string.wg_done_desc,imageRes = listOf(R.drawable.webhooklisto))
    )

    private var currentStep = 0

    private lateinit var dotsContainer: LinearLayout
    private lateinit var textStepProgress: TextView
    private lateinit var imageStepIcon: ImageView
    private lateinit var textStepTitle: TextView
    private lateinit var textStepDesc: TextView
    private lateinit var btnExternalAction: Button
    private lateinit var frameStepImage: FrameLayout
    private lateinit var layoutImagePlaceholder: LinearLayout
    private lateinit var carouselImages: HorizontalScrollView
    private lateinit var layoutImagesContainer: LinearLayout
    private lateinit var textImageHint: TextView
    private lateinit var btnStepAction: Button
    private lateinit var btnStepBack: Button
    private lateinit var btnStepSkip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webhook_guide)

        dotsContainer = findViewById(R.id.dotsGuideContainer)
        textStepProgress = findViewById(R.id.textGuideStepProgress)
        imageStepIcon = findViewById(R.id.imageGuideStepIcon)
        textStepTitle = findViewById(R.id.textGuideStepTitle)
        textStepDesc = findViewById(R.id.textGuideStepDesc)
        btnExternalAction = findViewById(R.id.btnGuideStepExternalAction)
        frameStepImage = findViewById(R.id.frameGuideStepImage)
        layoutImagePlaceholder = findViewById(R.id.layoutGuideStepImagePlaceholder)
        carouselImages = findViewById(R.id.carouselGuideStepImages)
        layoutImagesContainer = findViewById(R.id.layoutGuideStepImagesContainer)
        textImageHint = findViewById(R.id.textGuideStepImageHint)
        btnStepAction = findViewById(R.id.btnGuideStepAction)
        btnStepBack = findViewById(R.id.btnGuideStepBack)
        btnStepSkip = findViewById(R.id.btnGuideStepSkip)

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

    private fun buildDots() {
        dotsContainer.removeAllViews()
        steps.indices.forEach { _ ->
            val dot = View(this)
            val params = LinearLayout.LayoutParams(0, 0).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.space_xs)
            }
            dot.layoutParams = params
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

        textStepProgress.text = getString(R.string.webhook_guide_step_of, currentStep + 1, steps.size)
        updateDots()

        imageStepIcon.setImageResource(step.iconRes)
        textStepTitle.text = getString(step.titleRes)
        textStepDesc.text = getString(step.descRes)

        renderImageSlot(step.imageRes)

        // Botón secundario para abrir la app/sitio de este paso (si aplica)
        if (step.actionLabelRes != null && (step.actionUrl != null || step.actionPackage != null)) {
            btnExternalAction.visibility = View.VISIBLE
            btnExternalAction.text = getString(step.actionLabelRes)
            btnExternalAction.setOnClickListener {
                if (step.actionPackage != null) openApp(step.actionPackage, step.actionUrl)
                else if (step.actionUrl != null) openUrl(step.actionUrl)
            }
        } else {
            btnExternalAction.visibility = View.GONE
        }

        btnStepBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        btnStepSkip.visibility = if (step.type == GuideStepType.DONE) View.INVISIBLE else View.VISIBLE

        when (step.type) {
            GuideStepType.DONE -> {
                btnStepAction.text = getString(R.string.wg_done_action)
                btnStepAction.setOnClickListener { finish() }
            }

            else -> {
                btnStepAction.text = if (step.type == GuideStepType.INTRO) {
                    getString(R.string.wg_intro_action)
                } else {
                    getString(R.string.wg_next)
                }
                btnStepAction.setOnClickListener { goNext() }
            }
        }
    }

    /**
     * Reconstruye el carrusel horizontal con una miniatura por cada
     * imagen del paso (o muestra el placeholder punteado si el paso
     * todavía no trae ninguna).
     *
     * Cada miniatura:
     *  - Se adapta a la relación de aspecto REAL de la imagen (se lee con
     *    inJustDecodeBounds, sin cargar el bitmap completo) pero con el
     *    ancho limitado al ancho disponible de la tarjeta. Antes, una
     *    captura muy panorámica (ej. 1280x477) terminaba con una miniatura
     *    de ~700dp de ancho que se salía de la pantalla; ahora se recorta
     *    la altura en su lugar, así la imagen siempre cabe (o casi cabe)
     *    en el ancho visible, sin deformarse.
     *  - Tiene esquinas redondeadas y un borde sutil (MaterialCardView),
     *    para que se vea como una tarjeta "tocable" y no una imagen suelta.
     *  - Trae una lupa superpuesta en la esquina inferior derecha, como
     *    pista visual de que se puede tocar para verla más grande.
     *  - Al tocarla, abre ImageViewerActivity para verla a tamaño real
     *    con zoom (pinch + doble tap), igual que antes.
     */
    private fun renderImageSlot(images: List<Int>) {
        layoutImagesContainer.removeAllViews()

        if (images.isEmpty()) {
            carouselImages.visibility = View.GONE
            textImageHint.visibility = View.GONE
            layoutImagePlaceholder.visibility = View.VISIBLE
            return
        }

        carouselImages.visibility = View.VISIBLE
        layoutImagePlaceholder.visibility = View.GONE
        textImageHint.visibility = View.VISIBLE

        val frameHeightPx = frameStepImage.layoutParams.height
        val marginEndPx = resources.getDimensionPixelSize(R.dimen.space_sm)
        val cornerRadiusPx = resources.getDimension(R.dimen.thumb_corner_radius)
        val badgeSizePx = resources.getDimensionPixelSize(R.dimen.thumb_zoom_badge_size)
        val badgeMarginPx = resources.getDimensionPixelSize(R.dimen.space_xs)
        val badgePaddingPx = resources.getDimensionPixelSize(R.dimen.space_xs)

        // Ancho disponible dentro de la tarjeta del paso (pantalla menos el
        // padding del ScrollView y el de la tarjeta, ambos a cada lado).
        val sidePaddingPx = resources.getDimensionPixelSize(R.dimen.space_lg) * 4
        val cardContentWidthPx = (resources.displayMetrics.widthPixels - sidePaddingPx)
            .coerceAtLeast(resources.getDimensionPixelSize(R.dimen.space_lg) * 4)

        // Con una sola imagen se usa casi todo el ancho disponible; con
        // varias, se deja un poco más angosta para que se asome la
        // siguiente y quede claro que el carrusel se puede deslizar.
        val maxThumbWidthPx = if (images.size == 1) {
            cardContentWidthPx
        } else {
            (cardContentWidthPx * 0.82f).toInt()
        }

        images.forEach { res ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, res, bounds)
            val aspect = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
            } else {
                1f
            }

            var thumbWidth = (frameHeightPx * aspect).toInt()
            var thumbHeight = frameHeightPx
            if (thumbWidth > maxThumbWidthPx) {
                thumbWidth = maxThumbWidthPx
                thumbHeight = (maxThumbWidthPx / aspect).toInt()
            }

            val card = FrameLayout(this).apply {
                background = ContextCompat.getDrawable(this@WebhookGuideActivity, R.drawable.bg_image_thumb)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.webhook_guide_image_zoom_cd)
                val ripple = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                foreground = ContextCompat.getDrawable(this@WebhookGuideActivity, ripple.resourceId)
                setOnClickListener { ImageViewerActivity.start(this@WebhookGuideActivity, res) }
            }

            val thumbImage = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(res)
                contentDescription = null
            }
            card.addView(
                thumbImage,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            )

            val zoomBadge = ImageView(this).apply {
                setImageResource(R.drawable.ic_zoom_in)
                background = ContextCompat.getDrawable(this@WebhookGuideActivity, R.drawable.bg_zoom_badge)
                setPadding(badgePaddingPx, badgePaddingPx, badgePaddingPx, badgePaddingPx)
                contentDescription = null
            }
            card.addView(
                zoomBadge,
                FrameLayout.LayoutParams(badgeSizePx, badgeSizePx).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = badgeMarginPx
                    bottomMargin = badgeMarginPx
                }
            )

            val cardParams = LinearLayout.LayoutParams(thumbWidth, thumbHeight).apply {
                this.marginEnd = marginEndPx
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutImagesContainer.addView(card, cardParams)
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

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }

    /** Intenta abrir la app instalada (por ejemplo Alexa); si no está instalada, ofrece buscarla en Play Store. */
    private fun openApp(packageName: String, fallbackUrl: String?) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: ActivityNotFoundException) {
            val url = fallbackUrl ?: "https://play.google.com/store/apps/details?id=$packageName"
            openUrl(url)
        }
    }

    companion object {
        // Enlace directo a la ficha de la skill "Virtual Smart Home" en
        // Amazon: si el usuario tiene la app de Alexa instalada, Android
        // suele abrirlo ahí directo en la página de la skill (con el
        // botón "Habilitar para usar" a la vista); si no, cae al
        // navegador, donde igual puede habilitarla o instalar la app.
        private const val ALEXA_SKILL_URL = "https://www.amazon.com/dp/B08FCKD62D"
    }
}
