"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerFcmToken = void 0;
const https_1 = require("firebase-functions/v2/https");
const firestore_1 = require("firebase-admin/firestore");
const admin_1 = require("./admin");
/**
 * Llamada desde la app Android cada vez que se genera/renueva el token FCM
 * del celular (típicamente en el login y en onNewToken de FirebaseMessagingService).
 */
exports.registerFcmToken = (0, https_1.onCall)(async (request) => {
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "Tenés que estar logueado.");
    }
    const token = (request.data?.token || "").toString();
    if (!token) {
        throw new https_1.HttpsError("invalid-argument", "Falta el token FCM.");
    }
    await admin_1.db
        .collection("users")
        .doc(request.auth.uid)
        .set({
        fcmTokens: firestore_1.FieldValue.arrayUnion(token),
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
    return { ok: true };
});
//# sourceMappingURL=users.js.map