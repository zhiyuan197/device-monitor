package com.shenliwei.devicewatch.web;

/**
 * A decoded WebSocket frame: flag, opcode and unmasked payload.
 */
public record WebSocketFrame(boolean fin, int opcode, byte[] payload) {
}
