package com.example.bandcolorreact.ui.widget

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * ImageView con pinch-to-zoom y arrastre (pan), para ver una captura a su
 * tamaño real en vez de solo la versión reducida que cabe en pantalla.
 * No depende de ninguna librería externa: usa Matrix + ScaleGestureDetector
 * a mano, que es más que suficiente para un visor de "toca para ampliar".
 *
 * Doble tap alterna entre el zoom normal (imagen completa, "fitCenter") y
 * un acercamiento fijo (3x) centrado en el punto donde se tocó.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixValues = FloatArray(9)
    private val imageMatrix2 = Matrix()

    private var minScale = 1f
    private var maxScale = 1f
    private var baseScale = 1f
    private var currentScale = 1f

    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var isDragging = false

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = newScale / currentScale
                currentScale = newScale
                imageMatrix2.postScale(factor, factor, detector.focusX, detector.focusY)
                constrainMatrix()
                imageMatrix = imageMatrix2
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetScale = if (currentScale > baseScale * 1.5f) baseScale else baseScale * 3f
                val factor = targetScale / currentScale
                currentScale = targetScale.coerceIn(minScale, maxScale)
                imageMatrix2.postScale(factor, factor, e.x, e.y)
                constrainMatrix()
                imageMatrix = imageMatrix2
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetMatrix()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        post { resetMatrix() }
    }

    /** Centra la imagen y calcula el "fitCenter" inicial (equivalente a currentScale = baseScale). */
    private fun resetMatrix() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return

        val fitScale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)
        // La escala "1x real" (un píxel de la imagen = un píxel de pantalla):
        val realScale = 1f

        baseScale = fitScale
        minScale = min(fitScale, realScale) * 0.9f
        // Deja acercar bastante más allá del tamaño real para ver detalle fino.
        maxScale = max(realScale, fitScale) * 4f
        currentScale = fitScale

        val dx = (viewWidth - drawableWidth * fitScale) / 2f
        val dy = (viewHeight - drawableHeight * fitScale) / 2f

        imageMatrix2.reset()
        imageMatrix2.postScale(fitScale, fitScale)
        imageMatrix2.postTranslate(dx, dy)
        imageMatrix = imageMatrix2
    }

    private fun constrainMatrix() {
        val d = drawable ?: return
        imageMatrix2.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val drawableWidth = d.intrinsicWidth * scale
        val drawableHeight = d.intrinsicHeight * scale

        var fixX = 0f
        var fixY = 0f

        if (drawableWidth <= width) {
            fixX = (width - drawableWidth) / 2f - transX
        } else if (transX > 0f) {
            fixX = -transX
        } else if (transX < width - drawableWidth) {
            fixX = (width - drawableWidth) - transX
        }

        if (drawableHeight <= height) {
            fixY = (height - drawableHeight) / 2f - transY
        } else if (transY > 0f) {
            fixY = -transY
        } else if (transY < height - drawableHeight) {
            fixY = (height - drawableHeight) - transY
        }

        imageMatrix2.postTranslate(fixX, fixY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastFocusX = event.x
                lastFocusY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleGestureDetector.isInProgress) {
                    val dx = event.x - lastFocusX
                    val dy = event.y - lastFocusY
                    if (dx * dx + dy * dy > 16f) isDragging = true
                    if (isDragging) {
                        imageMatrix2.postTranslate(dx, dy)
                        constrainMatrix()
                        imageMatrix = imageMatrix2
                    }
                    lastFocusX = event.x
                    lastFocusY = event.y
                }
            }
        }
        return true
    }
}
