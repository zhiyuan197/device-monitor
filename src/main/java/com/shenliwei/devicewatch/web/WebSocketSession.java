package com.shenliwei.devicewatch.web;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * One upgraded browser connection. Writes are serialized under a lock.
 */
public final class WebSocketSession implements Closeable {
    private final Socket socket;
    private final Object writeLock = new Object();
    private volatile boolean open = true;

    public WebSocketSession(Socket socket) {
        this.socket = socket;
    }

    public void sendText(String text) {
        write(WebSocketFrameCodec.encodeText(text));
    }

    public void sendClose(int code, String reason) {
        write(WebSocketFrameCodec.encodeClose(code, reason));
    }

    public void sendPong(byte[] payload) {
        write(WebSocketFrameCodec.encodePong(payload));
    }

    public boolean isOpen() {
        return open && !socket.isClosed();
    }

    private void write(byte[] frame) {
        synchronized (writeLock) {
            if (!open) {
                return;
            }
            try {
                OutputStream out = socket.getOutputStream();
                out.write(frame);
                out.flush();
            } catch (IOException exception) {
                close();
            }
        }
    }

    @Override
    public void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Socket may already be closed.
        }
    }
}
