package com.shenliwei.devicewatch.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.protocol.ProtocolException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSocketFrameCodecTest {

    @Test
    void encodeTextProducesUnmaskedFinTextFrame() {
        byte[] expected = new byte[] {
            (byte) 0x81, 0x05, (byte) 'H', (byte) 'e', (byte) 'l', (byte) 'l', (byte) 'o'
        };
        assertArrayEquals(expected, WebSocketFrameCodec.encodeText("Hello"));
    }

    @Test
    void encodeTextUsesSixteenBitLengthForPayloadOf130() {
        String text = "a".repeat(130);
        byte[] frame = WebSocketFrameCodec.encodeText(text);
        assertEquals(0x81, frame[0] & 0xFF);
        assertEquals(126, frame[1] & 0x7F);
        int length = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        assertEquals(130, length);
    }

    @Test
    void encodeCloseProducesCodeAndReason() {
        byte[] frame = WebSocketFrameCodec.encodeClose(1000, "");
        assertArrayEquals(new byte[] {(byte) 0x88, 0x02, 0x03, (byte) 0xE8}, frame);
    }

    @Test
    void readFrameDecodesRfc6455Section57MaskedExample() throws Exception {
        byte[] wire = new byte[] {
            (byte) 0x81, (byte) 0x85,
            0x37, (byte) 0xFA, 0x21, 0x3D,
            0x7F, (byte) 0x9F, 0x4D, 0x51, 0x58
        };
        WebSocketFrame frame = WebSocketFrameCodec.readFrame(
                new ByteArrayInputStream(wire), 1024);
        assertTrue(frame.fin());
        assertEquals(WebSocketFrameCodec.OP_TEXT, frame.opcode());
        assertEquals("Hello", new String(frame.payload(), StandardCharsets.UTF_8));
    }

    @Test
    void readFrameRejectsUnmaskedClientFrame() {
        byte[] wire = new byte[] {(byte) 0x81, 0x05, 'H', 'e', 'l', 'l', 'o'};
        assertThrows(ProtocolException.class,
                () -> WebSocketFrameCodec.readFrame(new ByteArrayInputStream(wire), 1024));
    }

    @Test
    void readFrameRejectsOversizedControlFrame() {
        byte[] wire = new byte[2 + 2 + 4 + 126];
        wire[0] = (byte) 0x89;
        wire[1] = (byte) 0xFE;
        wire[2] = 0x00;
        wire[3] = 0x7E;
        assertThrows(ProtocolException.class,
                () -> WebSocketFrameCodec.readFrame(new ByteArrayInputStream(wire), 1024));
    }

    @Test
    void readFrameRejectsPayloadAboveLimit() {
        byte[] wire = new byte[] {(byte) 0x81, 0x7E, 0x04, 0x00, 0, 0, 0, 0};
        assertThrows(ProtocolException.class,
                () -> WebSocketFrameCodec.readFrame(new ByteArrayInputStream(wire), 512));
    }

    @Test
    void readFrameRejectsInvalidUtf8InTextFrame() {
        byte[] wire = new byte[] {
            (byte) 0x81, (byte) 0x82, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xC3, 0x28
        };
        assertThrows(ProtocolException.class,
                () -> WebSocketFrameCodec.readFrame(new ByteArrayInputStream(wire), 1024));
    }

    @Test
    void assemblerJoinsFragmentedTextMessage() throws Exception {
        WebSocketMessageAssembler assembler = new WebSocketMessageAssembler();
        assembler.accept(maskedTextFrame("Hel", false));
        assembler.accept(new WebSocketFrame(
                true, WebSocketFrameCodec.OP_CONTINUATION,
                "lo".getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("Hello"), assembler.drainTextMessages());
    }

    @Test
    void assemblerRejectsContinuationWithoutStart() {
        WebSocketMessageAssembler assembler = new WebSocketMessageAssembler();
        assertThrows(ProtocolException.class, () -> assembler.accept(new WebSocketFrame(
                true, WebSocketFrameCodec.OP_CONTINUATION,
                "x".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void assemblerRejectsInvalidUtf8AcrossFragments() throws Exception {
        WebSocketMessageAssembler assembler = new WebSocketMessageAssembler();
        assembler.accept(maskedFrame(new byte[] {(byte) 0xC3},
                WebSocketFrameCodec.OP_TEXT, false));
        assertThrows(ProtocolException.class, () -> assembler.accept(new WebSocketFrame(
                true, WebSocketFrameCodec.OP_CONTINUATION,
                new byte[] {0x28})));
    }

    private static WebSocketFrame maskedTextFrame(String text, boolean fin) {
        return maskedFrame(text.getBytes(StandardCharsets.UTF_8),
                WebSocketFrameCodec.OP_TEXT, fin);
    }

    private static WebSocketFrame maskedFrame(byte[] payload, int opcode, boolean fin) {
        byte[] mask = new byte[] {1, 2, 3, 4};
        byte[] wire = new byte[2 + 4 + payload.length];
        wire[0] = (byte) ((fin ? 0x80 : 0x00) | opcode);
        wire[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, wire, 2, 4);
        for (int i = 0; i < payload.length; i++) {
            wire[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        try {
            return WebSocketFrameCodec.readFrame(
                    new ByteArrayInputStream(wire), 1024);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void assemblerIgnoresBinaryFramesAndCollectsControlFrames() throws Exception {
        WebSocketMessageAssembler assembler = new WebSocketMessageAssembler();
        WebSocketFrame binary = new WebSocketFrame(
                true, WebSocketFrameCodec.OP_BINARY, new byte[] {1, 2});
        WebSocketFrame ping = new WebSocketFrame(
                true, WebSocketFrameCodec.OP_PING, new byte[] {'p'});
        assembler.accept(binary);
        assembler.accept(ping);
        assertEquals(List.of(ping), assembler.drainControlFrames());
        assertTrue(assembler.drainTextMessages().isEmpty());
    }

    @Test
    void encodeTextWith64BitLengthRoundTrips() {
        String text = "x".repeat(70_000);
        byte[] frame = WebSocketFrameCodec.encodeText(text);
        assertEquals(127, frame[1] & 0x7F);
        int length = frame.length - 10;
        assertEquals(70_000, length);
    }
}
