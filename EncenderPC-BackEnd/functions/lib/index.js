"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerFcmToken = exports.onDeviceStatusChange = exports.checkOfflineDevices = exports.pairDevice = exports.createDevice = void 0;
var pairing_1 = require("./pairing");
Object.defineProperty(exports, "createDevice", { enumerable: true, get: function () { return pairing_1.createDevice; } });
Object.defineProperty(exports, "pairDevice", { enumerable: true, get: function () { return pairing_1.pairDevice; } });
var presence_1 = require("./presence");
Object.defineProperty(exports, "checkOfflineDevices", { enumerable: true, get: function () { return presence_1.checkOfflineDevices; } });
Object.defineProperty(exports, "onDeviceStatusChange", { enumerable: true, get: function () { return presence_1.onDeviceStatusChange; } });
var users_1 = require("./users");
Object.defineProperty(exports, "registerFcmToken", { enumerable: true, get: function () { return users_1.registerFcmToken; } });
//# sourceMappingURL=index.js.map