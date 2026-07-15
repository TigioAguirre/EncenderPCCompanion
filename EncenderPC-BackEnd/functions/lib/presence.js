"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onDeviceStatusChange = exports.checkOfflineDevices = void 0;
const scheduler_1 = require("firebase-functions/v2/scheduler");
const firestore_1 = require("firebase-functions/v2/firestore");
const messaging_1 = require("firebase-admin/messaging");
const admin_1 = require("./admin");
/**
 * Corre cada minuto. Cualquier PC marcada "online" cuyo último heartbeat
 * sea más viejo que el umbral se pasa a "offline". Esto cubre apagados,
 * pérdida de red o crash del agente, sin depender de que el agente
 * avise "me estoy apagando" (que a veces no llega a tiempo).
 */
exports.checkOfflineDevices = (0, scheduler_1.onSchedule)("every 1 minutes", async () => {
    const staleCutoff = (0, admin_1.secondsAgo)(admin_1.OFFLINE_THRESHOLD_SECONDS);
    const staleDevices = await admin_1.db
        .collection("devices")
        .where("status", "==", "online")
        .where("lastSeen", "<", staleCutoff)
        .get();
    if (staleDevices.empty)
        return;
    const batch = admin_1.db.batch();
    staleDevices.forEach((doc) => {
        batch.update(doc.ref, { status: "offline" });
    });
    await batch.commit();
    console.log(`Marcadas ${staleDevices.size} PC(s) como offline por falta de heartbeat.`);
});
/**
 * Se dispara en cada update de devices/{deviceId}. Si el estado cambió
 * entre "online" y "offline" (en cualquier dirección), le manda una
 * push al dueño avisando. El paso a "online" es lo que dispara la
 * notificación cuando presionás play/pause en la pulsera y la PC
 * arranca; el paso a "offline" puede venir del propio agente (apagado
 * prolijo) o de checkOfflineDevices (falta de heartbeat).
 */
exports.onDeviceStatusChange = (0, firestore_1.onDocumentUpdated)("devices/{deviceId}", async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after)
        return;
    const justTurnedOn = before.status === "offline" && after.status === "online";
    const justTurnedOff = before.status === "online" && after.status === "offline";
    if (!justTurnedOn && !justTurnedOff)
        return;
    const ownerUid = after.ownerUid;
    const deviceName = after.name || "Tu PC";
    const userSnap = await admin_1.db.collection("users").doc(ownerUid).get();
    const tokens = userSnap.exists ? userSnap.data()?.fcmTokens || [] : [];
    if (tokens.length === 0) {
        console.log(`Usuario ${ownerUid} no tiene tokens FCM registrados, no se manda push.`);
        return;
    }
    const { title, body, type } = justTurnedOn
        ? {
            title: "PC encendida 🔥",
            body: `${deviceName} ya está prendida y conectada.`,
            type: "device_online",
        }
        : {
            title: "PC apagada",
            body: `${deviceName} se apagó.`,
            type: "device_offline",
        };
    const response = await (0, messaging_1.getMessaging)().sendEachForMulticast({
        tokens,
        notification: { title, body },
        data: {
            deviceId: event.params.deviceId,
            type,
        },
        android: {
            priority: "high",
        },
    });
    // Limpieza: si algún token quedó inválido (app desinstalada, etc.), lo sacamos.
    const invalidTokens = response.responses
        .map((r, i) => (!r.success ? tokens[i] : null))
        .filter((t) => t !== null);
    if (invalidTokens.length > 0) {
        await admin_1.db
            .collection("users")
            .doc(ownerUid)
            .update({
            fcmTokens: tokens.filter((t) => !invalidTokens.includes(t)),
        });
    }
});
//# sourceMappingURL=presence.js.map