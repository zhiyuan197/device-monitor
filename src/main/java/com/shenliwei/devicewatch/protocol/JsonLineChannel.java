package com.shenliwei.devicewatch.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Sends and receives newline-delimited JSON frames with a bounded frame size.
 */
public final class JsonLineChannel implements Closeable {
    private final Socket socket;
    private final InputStream input;
    private final BufferedWriter writer;
    private final Gson gson;
    private final int maxFrameBytes;
    private final Object writeLock = new Object();

    public JsonLineChannel(Socket socket, Gson gson, int maxFrameBytes) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.maxFrameBytes = maxFrameBytes;
        this.input = new BufferedInputStream(socket.getInputStream());
        this.writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public JsonObject read() throws IOException, ProtocolException {
        byte[] frame = readFrame();
        if (frame == null) {
            return null;
        }

        String json = new String(frame, StandardCharsets.UTF_8);
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw ProtocolException.invalidJson("JSON frame must be an object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw ProtocolException.invalidJson("Malformed JSON frame");
        }
    }

    public void write(JsonObject message) throws IOException {
        String json = gson.toJson(message);
        synchronized (writeLock) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }
    }

    private byte[] readFrame() throws IOException, ProtocolException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        while (true) {
            int next = input.read();
            if (next == -1) {
                if (buffer.size() == 0) {
                    return null;
                }
                break;
            }
            if (next == '\n') {
                break;
            }
            if (buffer.size() >= maxFrameBytes) {
                throw ProtocolException.tooLarge(maxFrameBytes);
            }
            buffer.write(next);
        }

        byte[] bytes = buffer.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        if (length == 0) {
            throw ProtocolException.invalidJson("Empty JSON frame");
        }
        byte[] trimmed = new byte[length];
        System.arraycopy(bytes, 0, trimmed, 0, length);
        return trimmed;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}

