package com.example.bandcolorreact.data

import android.util.Log
import com.example.bandcolorreact.model.Device
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * NOTA: los nombres de campo/colección ("devices", "ownerUid") son la
 * convención más común para este tipo de esquema, pero como las Cloud
 * Functions (createDevice, pairDevice, etc.) ya están desplegadas,
 * conviene confirmarlos contra el código real en tu carpeta `functions/`
 * antes de compilar. Si algo no coincide, es solo cuestión de ajustar
 * los strings de acá.
 */
class DeviceRepository(
    private val functionsRegion: String = "us-central1"
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance(functionsRegion)
    private val auth = FirebaseAuth.getInstance()

    /** Escucha en tiempo real las PCs del usuario logueado. */
    fun listenDevices(
        onChange: (List<Device>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError(IllegalStateException("No hay usuario logueado"))
            return null
        }

        return firestore.collection("devices")
            .whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                // mapNotNull con try/catch por documento: si UNO tiene un
                // campo con un tipo inesperado (por ejemplo lastSeen mal
                // guardado), lo salteamos y logueamos en vez de tumbar
                // toda la lista (y con ella, la Activity entera).
                val devices = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Device::class.java)?.apply { id = doc.id }
                    } catch (e: Exception) {
                        Log.e("DeviceRepository", "No se pudo parsear el documento ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                onChange(devices)
            }
    }

    /**
     * Llama a la Cloud Function `createDevice`. Devuelve el código de
     * emparejamiento de 6 dígitos que hay que ingresar en el agente de
     * Windows (`EncenderPCAgent.exe pair`).
     */
    suspend fun createDevice(deviceName: String): Result<String> {
        return try {
            val data = hashMapOf("name" to deviceName)
            val result = functions
                .getHttpsCallable("createDevice")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<String, Any?>
            val pairingCode = response?.get("pairingCode") as? String
                ?: return Result.failure(IllegalStateException("La función no devolvió un código de emparejamiento"))

            Result.success(pairingCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Registra el token de FCM de este celular vía la Cloud Function `registerFcmToken`. */
    suspend fun registerFcmToken(token: String): Result<Unit> {
        return try {
            val data = hashMapOf("token" to token)
            functions.getHttpsCallable("registerFcmToken").call(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Borra el documento de la PC directo en Firestore (no hace falta pasar
     * por una Cloud Function): firestore.rules ya permite `delete` cuando
     * `resource.data.ownerUid` coincide con el usuario logueado, así que
     * un intento de borrar una PC ajena es rechazado del lado del servidor
     * aunque alguien manipule el cliente.
     */
    suspend fun deleteDevice(deviceId: String): Result<Unit> {
        return try {
            firestore.collection("devices").document(deviceId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
