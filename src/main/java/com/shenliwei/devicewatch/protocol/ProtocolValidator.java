package com.shenliwei.devicewatch.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class ProtocolValidator {
    private ProtocolValidator() {
    }

    public static String requiredString(JsonObject object, String field, int maxLength)
            throws ProtocolException {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw ProtocolException.validation(field + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw ProtocolException.validation(field + " must be a string");
        }
        String value = primitive.getAsString().trim();
        if (value.isEmpty()) {
            throw ProtocolException.validation(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw ProtocolException.validation(field + " is too long");
        }
        return value;
    }

    public static double requiredDouble(JsonObject object, String field) throws ProtocolException {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw ProtocolException.validation(field + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw ProtocolException.validation(field + " must be a number");
        }
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value)) {
            throw ProtocolException.validation(field + " must be finite");
        }
        return value;
    }

    public static Double optionalDouble(JsonObject object, String field)
            throws ProtocolException {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw ProtocolException.validation(field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw ProtocolException.validation(field + " must be finite");
        }
        return value;
    }

    public static long requiredLong(JsonObject object, String field) throws ProtocolException {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw ProtocolException.validation(field + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw ProtocolException.validation(field + " must be a number");
        }
        try {
            return primitive.getAsLong();
        } catch (NumberFormatException exception) {
            throw ProtocolException.validation(field + " must be an integer");
        }
    }

    public static boolean requiredBoolean(JsonObject object, String field)
            throws ProtocolException {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw ProtocolException.validation(field + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            throw ProtocolException.validation(field + " must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    public static String requiredType(JsonObject message) throws ProtocolException {
        return requiredString(message, "type", 64);
    }

    public static String requiredRequestId(JsonObject message) throws ProtocolException {
        return requiredString(message, "requestId", 64);
    }
}

