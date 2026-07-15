"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.OFFLINE_THRESHOLD_SECONDS = exports.db = void 0;
exports.secondsAgo = secondsAgo;
const app_1 = require("firebase-admin/app");
const firestore_1 = require("firebase-admin/firestore");
(0, app_1.initializeApp)();
exports.db = (0, firestore_1.getFirestore)();
// Tiempo sin heartbeat después del cual consideramos la PC apagada.
// El agente manda heartbeat cada 30s -> 90s da margen a 2 heartbeats perdidos
// (por ejemplo un pico de red) antes de marcarla offline.
exports.OFFLINE_THRESHOLD_SECONDS = 90;
function secondsAgo(seconds) {
    return firestore_1.Timestamp.fromMillis(Date.now() - seconds * 1000);
}
//# sourceMappingURL=admin.js.map