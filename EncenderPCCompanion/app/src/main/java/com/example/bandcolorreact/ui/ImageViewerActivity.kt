package com.example.bandcolorreact.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.bandcolorreact.R
import com.example.bandcolorreact.ui.widget.ZoomableImageView

/**
 * Visor de pantalla completa para una captura de la guía. Se abre al tocar
 * una miniatura del carrusel en [WebhookGuideActivity]. Permite pinch-zoom
 * y arrastre (ver [ZoomableImageView]) para inspeccionar la imagen a su
 * tamaño real, no solo la versión reducida que cabe en la miniatura.
 */
class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val imageRes = intent.getIntExtra(EXTRA_IMAGE_RES, 0)
        val zoomableImage = findViewById<ZoomableImageView>(R.id.imageViewerFull)
        if (imageRes != 0) {
            zoomableImage.setImageResource(imageRes)
        }

        findViewById<ImageButton>(R.id.btnImageViewerClose).setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_IMAGE_RES = "extra_image_res"

        fun start(context: Context, imageRes: Int) {
            context.startActivity(
                Intent(context, ImageViewerActivity::class.java).putExtra(EXTRA_IMAGE_RES, imageRes)
            )
        }
    }
}
