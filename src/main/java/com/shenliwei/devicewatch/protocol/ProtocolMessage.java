package com.shenliwei.devicewatch.protocol;

import com.google.gson.JsonObject;

public final class ProtocolMessage {
    private ProtocolMessage() {
    }

    public static JsonObject request(String type, String requestId, long timestamp, JsonObject payload) {
        JsonObject message = base(type, requestId, timestamp);
        message.add("payload", payload == null ? new JsonObject() : payload);
        return message;
    }

    public static JsonObject response(String type, String requestId, long timestamp, JsonObject payload) {
        return request(type, requestId, timestamp, payload);
    }

    public static JsonObject error(
            String requestId, long timestamp, String code, String detail, boolean recoverable) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        payload.addProperty("message", detail);
        payload.addProperty("recoverable", recoverable);
        return response("ERROR", requestId, timestamp, payload);
    }

    private static JsonObject base(String type, String requestId, long timestamp) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        message.addProperty("requestId", requestId);
        message.addProperty("timestamp", timestamp);
        return message;
    }
}

