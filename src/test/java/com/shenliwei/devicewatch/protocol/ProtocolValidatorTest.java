package com.shenliwei.devicewatch.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ProtocolValidatorTest {

    @Test
    void optionalDoubleReadsPresentValue() throws Exception {
        JsonObject object = JsonParser.parseString("{\"diskPercent\": 41.5}").getAsJsonObject();
        assertEquals(41.5, ProtocolValidator.optionalDouble(object, "diskPercent"));
    }

    @Test
    void optionalDoubleReturnsNullWhenAbsent() throws Exception {
        assertEquals(null, ProtocolValidator.optionalDouble(new JsonObject(), "diskPercent"));
    }

    @Test
    void optionalDoubleRejectsNonFiniteOrWrongType() throws Exception {
        JsonObject nan = JsonParser.parseString("{\"diskPercent\": null}").getAsJsonObject();
        assertNull(ProtocolValidator.optionalDouble(nan, "diskPercent"));
        JsonObject wrongType =
                JsonParser.parseString("{\"diskPercent\": \"41\"}").getAsJsonObject();
        assertThrows(ProtocolException.class,
                () -> ProtocolValidator.optionalDouble(wrongType, "diskPercent"));
    }
}
