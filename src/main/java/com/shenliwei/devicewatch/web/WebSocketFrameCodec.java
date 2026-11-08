package com.shenliwei.devicewatch.web;

import com.shenliwei.devicewatch.protocol.ProtocolException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 6455 frame codec for a server endpoint: unmasked writes,
 * masked reads, strict size and UTF-8 validation.
 */
public final class WebSocketFrameCodec {

    public static final int OP_CONTINUATION = 0x0;
    public static final int OP_TEXT = 0x1;
    public static final int OP_BINARY = 0x2;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    private WebSocketFrameCodec() {
    }

    public static byte[] encodeText(String text) {
        return encode(OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encodeClose(int code, String reason) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write((code >> 8) & 0xFF);
        payload.write(code & 0xFF);
        try {
            payload.write(reason.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return encode(OP_CLOSE, payload.toByteArray());
    }

    public static byte[] encodePong(byte[] payload) {
        return encode(OP_PONG, payload);
    }

    private static byte[] encode(int opcode, byte[] payload) {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        int length = payload.length;
        if (length <= 125) {
            frame.write(length);
        } else if (length <= 0xFFFF) {
            frame.write(126);
            frame.write((length >> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.write((int) (((long) length >> shift) & 0xFF));
            }
        }
        frame.write(payload, 0, payload.length);
        return frame.toByteArray();
    }

    public static WebSocketFrame readFrame(InputStream in, int maxPayloadBytes)
            throws IOException, ProtocolException {
        return readFrame(in, maxPayloadBytes, true);
    }

    public static WebSocketFrame readFrame(
            InputStream in, int maxPayloadBytes, boolean requireMask)
            throws IOException, ProtocolException {
        int first = in.read();
        int second = in.read();
        if (first < 0 || second < 0) {
            throw new IOException("connection closed mid-frame");
        }
        if ((first & 0x70) != 0) {
            throw ProtocolException.validation("reserved bits must be zero");
        }
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = (long) readByte(in) << 8 | readByte(in);
        } else if (length == 127) {
            long extended = 0;
            for (int i = 0; i < 8; i++) {
                extended = (extended << 8) | readByte(in);
            }
            if ((extended & 0x8000_0000_0000_0000L) != 0) {
                throw ProtocolException.validation("frame length exceeds signed 64-bit range");
            }
            length = extended;
        }
        if (requireMask && !masked) {
            throw ProtocolException.validation("client frames must be masked");
        }
        if (length > maxPayloadBytes) {
            throw ProtocolException.validation("frame too large");
        }
        boolean control = opcode >= 0x8;
        if (control && (!fin || length > 125)) {
            throw ProtocolException.validation(
                    "control frames must be final and at most 125 bytes");
        }
        byte[] mask = new byte[4];
        if (masked) {
            readFully(in, mask);
        }
        byte[] payload = new byte[(int) length];
        readFully(in, payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % 4];
            }
        }
        if (opcode == OP_TEXT && fin) {
            requireValidUtf8(payload);
        }
        return new WebSocketFrame(fin, opcode, payload);
    }

    static void requireValidUtf8(byte[] payload) throws ProtocolException {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload));
        } catch (CharacterCodingException exception) {
            throw ProtocolException.validation("text payload is not valid UTF-8");
        }
    }

    private static int readByte(InputStream in) throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new IOException("connection closed mid-frame");
        }
        return value;
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                throw new IOException("connection closed mid-frame");
            }
            offset += read;
        }
    }
}
