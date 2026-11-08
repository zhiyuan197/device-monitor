package com.shenliwei.devicewatch.web;

import com.shenliwei.devicewatch.protocol.ProtocolException;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Minimal loopback HTTP/1.1 server that serves the bundled dashboard
 * assets and upgrades the /ws endpoint to an RFC 6455 WebSocket.
 */
public final class WebServer implements Closeable {

    public static final int MAX_CONNECTIONS = 32;

    private static final Map<String, String> ROUTES = Map.of(
            "/", "/web/index.html",
            "/index.html", "/web/index.html",
            "/app.js", "/web/app.js",
            "/style.css", "/web/style.css",
            "/echarts.min.js", "/web/echarts.min.js");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "/web/index.html", "text/html; charset=UTF-8",
            "/web/app.js", "application/javascript; charset=UTF-8",
            "/web/style.css", "text/css; charset=UTF-8",
            "/web/echarts.min.js", "application/javascript; charset=UTF-8");

    private final String bindHost;
    private final int configuredPort;
    private final Consumer<WebSocketSession> sessionListener;
    private final CopyOnWriteArrayList<WebSocketSession> sessions =
            new CopyOnWriteArrayList<>();
    private final AtomicInteger connectionCount = new AtomicInteger();

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptor;
    private volatile boolean running;

    public WebServer(String bindHost, int port, Consumer<WebSocketSession> sessionListener) {
        this.bindHost = bindHost;
        this.configuredPort = port;
        this.sessionListener = sessionListener;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(bindHost), configuredPort));
        serverSocket = socket;
        running = true;
        acceptor = new Thread(this::acceptLoop, "web-dashboard-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public boolean isRunning() {
        return running;
    }

    public int port() {
        ServerSocket socket = serverSocket;
        return socket == null ? configuredPort : socket.getLocalPort();
    }

    public String baseUrl() {
        return "http://" + bindHost + ":" + port() + "/";
    }

    public List<WebSocketSession> sessions() {
        return List.copyOf(sessions);
    }

    public void broadcastText(String text) {
        for (WebSocketSession session : sessions) {
            session.sendText(text);
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread handler = new Thread(() -> handleClient(client), "web-dashboard-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException exception) {
                if (running) {
                    continue;
                }
            }
        }
    }

    private void handleClient(Socket client) {
        connectionCount.incrementAndGet();
        try (Socket socket = client) {
            socket.setSoTimeout(10_000);
            if (connectionCount.get() > MAX_CONNECTIONS) {
                respond(socket, 503, "Service Unavailable", null, null);
                return;
            }
            HttpRequest request = HttpRequestParser.parse(socket.getInputStream());
            route(socket, request);
        } catch (IOException exception) {
            respondBestEffort(client, 400, "Bad Request");
        } finally {
            connectionCount.decrementAndGet();
        }
    }

    private void route(Socket socket, HttpRequest request) throws IOException {
        if (!"GET".equals(request.method())) {
            respond(socket, 405, "Method Not Allowed", null, null);
            return;
        }
        if ("/ws".equals(request.path())) {
            upgrade(socket, request);
            return;
        }
        String resource = ROUTES.get(request.path());
        if (resource == null) {
            respond(socket, 404, "Not Found", null, null);
            return;
        }
        byte[] body = readResource(resource);
        if (body == null) {
            respond(socket, 404, "Not Found", null, null);
            return;
        }
        respond(socket, 200, "OK", body, CONTENT_TYPES.get(resource));
    }

    private void upgrade(Socket socket, HttpRequest request) throws IOException {
        String accept;
        try {
            accept = WebSocketHandshake.validate(request.headers());
        } catch (HandshakeException exception) {
            respond(socket, exception.statusCode(), statusText(exception.statusCode()),
                    exception.reason().getBytes(StandardCharsets.UTF_8),
                    "text/plain; charset=UTF-8");
            return;
        }
        OutputStream out = socket.getOutputStream();
        String head = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        socket.setSoTimeout(0);

        WebSocketSession session = new WebSocketSession(socket);
        sessions.add(session);
        if (sessionListener != null) {
            sessionListener.accept(session);
        }
        WebSocketMessageAssembler assembler = new WebSocketMessageAssembler();
        while (session.isOpen()) {
            try {
                WebSocketFrame frame = WebSocketFrameCodec.readFrame(
                        socket.getInputStream(), WebSocketFrameCodec.MAX_PAYLOAD_BYTES);
                assembler.accept(frame);
                assembler.drainTextMessages();
                for (WebSocketFrame control : assembler.drainControlFrames()) {
                    if (control.opcode() == WebSocketFrameCodec.OP_PING) {
                        session.sendPong(control.payload());
                    } else if (control.opcode() == WebSocketFrameCodec.OP_CLOSE) {
                        session.sendClose(1000, "");
                        session.close();
                    }
                }
            } catch (ProtocolException exception) {
                session.sendClose(1002, "protocol error");
                session.close();
            } catch (IOException exception) {
                session.close();
            }
        }
        sessions.remove(session);
        session.close();
    }

    private byte[] readResource(String resource) {
        try (InputStream input = WebServer.class.getResourceAsStream(resource)) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    private static void respond(Socket socket, int code, String statusText,
            byte[] body, String contentType) throws IOException {
        OutputStream out = socket.getOutputStream();
        StringBuilder head = new StringBuilder(160);
        head.append("HTTP/1.1 ").append(code).append(' ').append(statusText).append("\r\n");
        if (contentType != null) {
            head.append("Content-Type: ").append(contentType).append("\r\n");
        }
        if (body != null) {
            head.append("Content-Length: ").append(body.length).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body != null) {
            out.write(body);
        }
        out.flush();
    }

    private static void respondBestEffort(Socket socket, int code, String statusText) {
        try {
            respond(socket, code, statusText, null, null);
        } catch (IOException ignored) {
            // Client is gone.
        }
    }

    private static String statusText(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 426 -> "Upgrade Required";
            default -> "Error";
        };
    }

    @Override
    public synchronized void close() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
        for (WebSocketSession session : sessions) {
            session.close();
        }
        sessions.clear();
        serverSocket = null;
    }
}
