package com.example.bandcolorreact

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

/**
 * Receptor "de respaldo" para el botón físico de la pulsera.
 *
 * Cuando registramos este receptor como el mediaButtonReceiver de nuestra
 * MediaSessionCompat (ver MusicButtonListenerService), Android lo usa como
 * destino explícito y fijo para el botón de play/pause, sin importar si:
 *   - nuestra sesión está activa o no en ese instante,
 *   - nuestro servicio/proceso sigue vivo o fue matado por el sistema.
 *
 * Esto evita que, ante la ausencia de una sesión activa, el sistema decida
 * abrir/despertar otra app de música por defecto en su lugar.
 *
 * Reaccionamos directamente al KeyEvent sin depender de que exista una
 * sesión "viva" en memoria en ese momento.
 */
class BandMediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return

        // Solo reaccionamos al "key down" para no disparar el color dos veces
        // (una en ACTION_DOWN y otra en ACTION_UP) por la misma pulsación.
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        Log.d(TAG, "BandMediaButtonReceiver recibió keyCode=${keyEvent.keyCode}")

        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                ColorOverlay.show(context.applicationContext, playingColor = true)
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                ColorOverlay.show(context.applicationContext, playingColor = false)
            }
        }
    }

    companion object {
        private const val TAG = "BandMediaButtonRcv"
    }
}
