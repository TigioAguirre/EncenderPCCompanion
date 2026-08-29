package com.example.bandcolorreact.data

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Utilidad compartida para "disparar" un webhook y, a diferencia del
 * disparo silencioso que hace MusicButtonListenerService al presionar el
 * botón de la pulsera, devolver un resultado (éxito/error + código HTTP)
 * que sí se le puede mostrar al usuario.
 *
 * La usan dos lugares:
 *  - El botón "Probar webhook ahora" de MainActivity (prueba el enlace ya
 *    guardado, sin importar si el interruptor principal está encendido).
 *  - El paso de prueba dentro de WebhookGuideActivity (prueba el enlace
 *    que el usuario acaba de generar, antes de guardarlo).
 */
object WebhookTrigger {

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    sealed class Result {
        data class Success(val code: Int) : Result()
        data class Failure(val message: String) : Result()
    }

    /**
     * Llama a [url] con un GET simple (mismo método que usa el disparo
     * real del botón de la pulsera) en un hilo de fondo, y entrega
     * [onResult] siempre en el hilo principal para poder actualizar la UI
     * sin preocuparse por hilos desde quien llama.
     */
    fun trigger(url: String, onResult: (Result) -> Unit) {
        executor.execute {
            val result: Result = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..299) Result.Success(code) else Result.Failure("HTTP $code")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Error de conexión")
            }
            mainHandler.post { onResult(result) }
        }
    }
}
