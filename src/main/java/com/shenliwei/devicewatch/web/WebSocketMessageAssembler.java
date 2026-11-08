package com.shenliwei.devicewatch.web;

import com.shenliwei.devicewatch.protocol.ProtocolException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reassembles fragmented text messages and routes control frames.
 */
public final class WebSocketMessageAssembler {

    private final List<String> textMessages = new ArrayList<>();
    private final List<WebSocketFrame> controlFrames = new ArrayList<>();
    private ByteArrayOutputStream fragmentedText;

    public void accept(WebSocketFrame frame) throws ProtocolException {
        if (frame.opcode() >= 0x8) {
            controlFrames.add(frame);
            return;
        }
        if (frame.opcode() == WebSocketFrameCodec.OP_BINARY) {
            return;
        }
        if (frame.opcode() == WebSocketFrameCodec.OP_TEXT) {
            if (fragmentedText != null) {
                throw ProtocolException.validation(
                        "new text message before continuation finished");
            }
            if (frame.fin()) {
                textMessages.add(text(frame.payload()));
                return;
            }
            fragmentedText = new ByteArrayOutputStream();
            appendFragment(frame.payload());
            return;
        }
        if (frame.opcode() == WebSocketFrameCodec.OP_CONTINUATION) {
            if (fragmentedText == null) {
                throw ProtocolException.validation(
                        "continuation frame without a started message");
            }
            appendFragment(frame.payload());
            if (frame.fin()) {
                textMessages.add(text(fragmentedText.toByteArray()));
                fragmentedText = null;
            }
        }
    }

    public List<String> drainTextMessages() {
        List<String> drained = List.copyOf(textMessages);
        textMessages.clear();
        return drained;
    }

    public List<WebSocketFrame> drainControlFrames() {
        List<WebSocketFrame> drained = List.copyOf(controlFrames);
        controlFrames.clear();
        return drained;
    }

    private void appendFragment(byte[] payload) throws ProtocolException {
        if (fragmentedText.size() + payload.length
                > WebSocketFrameCodec.MAX_PAYLOAD_BYTES) {
            throw ProtocolException.validation("fragmented message too large");
        }
        fragmentedText.write(payload, 0, payload.length);
    }

    private static String text(byte[] payload) throws ProtocolException {
        WebSocketFrameCodec.requireValidUtf8(payload);
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }
}
