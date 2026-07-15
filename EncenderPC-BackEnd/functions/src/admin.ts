import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

initializeApp();

export const db = getFirestore();

// Tiempo sin heartbeat después del cual consideramos la PC apagada.
// El agente manda heartbeat cada 30s -> 90s da margen a 2 heartbeats perdidos
// (por ejemplo un pico de red) antes de marcarla offline.
export const OFFLINE_THRESHOLD_SECONDS = 90;

export function secondsAgo(seconds: number): Timestamp {
  return Timestamp.fromMillis(Date.now() - seconds * 1000);
}
