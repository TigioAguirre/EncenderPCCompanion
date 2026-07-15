package com.example.bandcolorreact.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Representa un documento de la colección `devices` en Firestore.
 * Ajustá los nombres de campo si en tus Cloud Functions usaste otros.
 *
 * `lastSeenRaw` acepta tanto un Timestamp de Firestore (lo más común
 * cuando se escribe con FieldValue.serverTimestamp() o vía el API REST)
 * como un número Long crudo, para no depender de cómo lo haya guardado
 * el agente de Windows o las Cloud Functions. `toObject()` de Firestore
 * puede tirar una excepción si el tipo no coincide exactamente con el
 * campo declarado, así que dejamos el campo como `Any?` y convertimos
 * nosotros mismos en `lastSeenMillis`.
 */
data class Device(
    var id: String = "",
    var name: String = "",

    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = "offline", // "online" | "offline"

    @get:PropertyName("lastSeen")
    @set:PropertyName("lastSeen")
    var lastSeenRaw: Any? = null
) {
    val isOnline: Boolean
        get() = status == "online"

    @get:Exclude
    val lastSeenMillis: Long
        get() = when (val raw = lastSeenRaw) {
            is Timestamp -> raw.toDate().time
            is Long -> raw
            is Double -> raw.toLong()
            is Number -> raw.toLong()
            else -> 0L
        }
}
