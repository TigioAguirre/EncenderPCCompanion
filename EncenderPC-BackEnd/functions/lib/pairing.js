"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.pairDevice = exports.createDevice = void 0;
const https_1 = require("firebase-functions/v2/https");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
const admin_1 = require("./admin");
const PAIRING_CODE_TTL_MINUTES = 10;
function generatePairingCode() {
    // Código de 6 dígitos, fácil de tipear a mano en la PC.
    return Math.floor(100000 + Math.random() * 900000).toString();
}
/**
 * Llamada desde la app Android cuando el usuario toca "Agregar PC".
 * Crea el documento de la PC (offline por defecto) y un código de
 * emparejamiento de un solo uso que el usuario tipea en el agente de Windows.
 */
exports.createDevice = (0, https_1.onCall)(async (request) => {
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "Tenés que estar logueado.");
    }
    const ownerUid = request.auth.uid;
    const name = (request.data?.name || "Mi PC").toString().slice(0, 60);
    const deviceRef = admin_1.db.collection("devices").doc();
    await deviceRef.set({
        ownerUid,
        name,
        status: "offline",
        lastSeen: null,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
    const code = generatePairingCode();
    const expiresAt = new Date(Date.now() + PAIRING_CODE_TTL_MINUTES * 60 * 1000);
    await admin_1.db.collection("pairingCodes").doc(code).set({
        deviceId: deviceRef.id,
        ownerUid,
        used: false,
        expiresAt,
    });
    return {
        deviceId: deviceRef.id,
        pairingCode: code,
        expiresInMinutes: PAIRING_CODE_TTL_MINUTES,
    };
});
/**
 * Llamada desde el agente de Windows (SIN sesión de Firebase todavía) con
 * el código de 6 dígitos que el usuario tipeó. Devuelve un custom token
 * que el agente cambia por credenciales reales (signInWithCustomToken) y
 * que, gracias al claim "deviceId", solo le permite escribir status/lastSeen
 * de ESA PC puntual (ver firestore.rules).
 */
exports.pairDevice = (0, https_1.onCall)(async (request) => {
    const code = (request.data?.pairingCode || "").toString().trim();
    if (!/^\d{6}$/.test(code)) {
        throw new https_1.HttpsError("invalid-argument", "Código inválido.");
    }
    const codeRef = admin_1.db.collection("pairingCodes").doc(code);
    const codeSnap = await codeRef.get();
    if (!codeSnap.exists) {
        throw new https_1.HttpsError("not-found", "Ese código no existe o ya expiró.");
    }
    const codeData = codeSnap.data();
    if (codeData.used) {
        throw new https_1.HttpsError("failed-precondition", "Ese código ya fue usado.");
    }
    if (codeData.expiresAt.toDate() < new Date()) {
        throw new https_1.HttpsError("deadline-exceeded", "Ese código expiró, generá uno nuevo desde la app.");
    }
    const deviceId = codeData.deviceId;
    await codeRef.update({ used: true });
    // Identidad sintética para el agente: no es un usuario real, es "la PC".
    const customToken = await (0, auth_1.getAuth)().createCustomToken(`device:${deviceId}`, {
        deviceId,
    });
    return { customToken, deviceId };
});
//# sourceMappingURL=pairing.js.map