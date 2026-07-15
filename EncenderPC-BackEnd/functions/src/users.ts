import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";

/**
 * Llamada desde la app Android cada vez que se genera/renueva el token FCM
 * del celular (típicamente en el login y en onNewToken de FirebaseMessagingService).
 */
export const registerFcmToken = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Tenés que estar logueado.");
  }
  const token: string = (request.data?.token || "").toString();
  if (!token) {
    throw new HttpsError("invalid-argument", "Falta el token FCM.");
  }

  await db
    .collection("users")
    .doc(request.auth.uid)
    .set(
      {
        fcmTokens: FieldValue.arrayUnion(token),
        createdAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

  return { ok: true };
});
