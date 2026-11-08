package com.shenliwei.devicewatch.simulator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.shenliwei.devicewatch.protocol.JsonLineChannel;
import com.shenliwei.devicewatch.protocol.ProtocolException;
import com.shenliwei.devicewatch.protocol.ProtocolMessage;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;

public final class DeviceClient implements Closeable {
    private final Socket socket;
    private final JsonLineChannel channel;
    private final String deviceId;

    private DeviceClient(Socket socket, JsonLineChannel channel, String deviceId) {
        this.socket = socket;
        this.channel = channel;
        this.deviceId = deviceId;
    }

    public static DeviceClient connect(SimulatorConfig config, SimulatorConfig.DeviceAccount device)
            throws IOException, ProtocolException, RemoteErrorException {
        return connect(config, device, false);
    }

    public static DeviceClient connect(
            SimulatorConfig config, SimulatorConfig.DeviceAccount device, boolean real)
            throws IOException, ProtocolException, RemoteErrorException {
        return connect(config, device, real,
                config.tlsEnabled(), config.truststorePath(), config.truststorePassword());
    }

    public static DeviceClient connect(
            SimulatorConfig config,
            SimulatorConfig.DeviceAccount device,
            boolean real,
            boolean tlsEnabled,
            Path truststorePath,
            String truststorePassword)
            throws IOException, ProtocolException, RemoteErrorException {
        Socket socket;
        if (tlsEnabled) {
            try {
                javax.net.ssl.SSLContext context =
                        com.shenliwei.devicewatch.common.TlsContextFactory.clientContext(
                                truststorePath, truststorePassword);
                socket = context.getSocketFactory().createSocket();
            } catch (java.security.GeneralSecurityException exception) {
                throw new IOException("TLS initialization failed", exception);
            }
        } else {
            socket = new Socket();
        }
        int targetPort = tlsEnabled ? config.tlsPort() : config.port();
        socket.connect(new InetSocketAddress(config.host(), targetPort), 5_000);
        socket.setSoTimeout(10_000);
        Gson gson = new GsonBuilder().serializeNulls().create();
        JsonLineChannel channel = new JsonLineChannel(socket, gson, 65_536);
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("role", "DEVICE");
            payload.addProperty("deviceId", device.deviceId());
            payload.addProperty("credential", device.credential());
            payload.addProperty("real", real);
            String requestId = UUID.randomUUID().toString();
            channel.write(ProtocolMessage.request(
                    "AUTH", requestId, System.currentTimeMillis(), payload));
            JsonObject response = channel.read();
            requireSuccessfulResponse(response, "AUTH_OK", requestId);
            return new DeviceClient(socket, channel, device.deviceId());
        } catch (IOException | ProtocolException | RemoteErrorException exception) {
            socket.close();
            throw exception;
        }
    }

    public String deviceId() {
        return deviceId;
    }

    public void sendHeartbeat(long sequence) throws IOException, ProtocolException, RemoteErrorException {
        JsonObject payload = new JsonObject();
        payload.addProperty("sequence", sequence);
        sendAndAwait("HEARTBEAT", "HEARTBEAT_ACK", payload);
    }

    public void sendTelemetry(
            long sequence,
            long collectedAt,
            double cpuPercent,
            double memoryPercent,
            double temperatureCelsius)
            throws IOException, ProtocolException, RemoteErrorException {
        sendTelemetry(sequence, collectedAt, cpuPercent, memoryPercent, temperatureCelsius, null);
    }

    public void sendTelemetry(
            long sequence,
            long collectedAt,
            double cpuPercent,
            double memoryPercent,
            double temperatureCelsius,
            Double diskPercent)
            throws IOException, ProtocolException, RemoteErrorException {
        JsonObject payload = new JsonObject();
        payload.addProperty("sequence", sequence);
        payload.addProperty("collectedAt", collectedAt);
        payload.addProperty("cpuPercent", cpuPercent);
        payload.addProperty("memoryPercent", memoryPercent);
        payload.addProperty("temperatureCelsius", temperatureCelsius);
        if (diskPercent != null) {
            payload.addProperty("diskPercent", diskPercent);
        }
        sendAndAwait("TELEMETRY", "TELEMETRY_ACK", payload);
    }

    private void sendAndAwait(String type, String expectedType, JsonObject payload)
            throws IOException, ProtocolException, RemoteErrorException {
        String requestId = UUID.randomUUID().toString();
        channel.write(ProtocolMessage.request(type, requestId, System.currentTimeMillis(), payload));
        JsonObject response = channel.read();
        requireSuccessfulResponse(response, expectedType, requestId);
    }

    private static void requireSuccessfulResponse(
            JsonObject response, String expectedType, String expectedRequestId)
            throws IOException, RemoteErrorException, ProtocolException {
        if (response == null) {
            throw new IOException("Server closed the connection");
        }
        String type = response.get("type").getAsString();
        String requestId = response.get("requestId").getAsString();
        if (!expectedRequestId.equals(requestId)) {
            throw ProtocolException.validation("Response requestId did not match");
        }
        if ("ERROR".equals(type)) {
            JsonObject payload = response.getAsJsonObject("payload");
            throw new RemoteErrorException(
                    payload.get("code").getAsString(),
                    payload.get("message").getAsString());
        }
        if (!expectedType.equals(type)) {
            throw ProtocolException.validation(
                    "Expected " + expectedType + " but received " + type);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            JsonObject payload = new JsonObject();
            channel.write(ProtocolMessage.request(
                    "LOGOUT", UUID.randomUUID().toString(), System.currentTimeMillis(), payload));
        } catch (IOException ignored) {
            // The socket may already be closed.
        } finally {
            socket.close();
        }
    }
}

