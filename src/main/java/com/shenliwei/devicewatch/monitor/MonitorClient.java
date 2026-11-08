package com.shenliwei.devicewatch.monitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.shenliwei.devicewatch.protocol.JsonLineChannel;
import com.shenliwei.devicewatch.protocol.MessageObserver;
import com.shenliwei.devicewatch.protocol.ProtocolException;
import com.shenliwei.devicewatch.protocol.ProtocolMessage;
import com.shenliwei.devicewatch.simulator.RemoteErrorException;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Authenticated monitor connection with asynchronous snapshots and queries.
 */
public final class MonitorClient implements Closeable {
    private final String host;
    private final int port;
    private final String username;
    private final char[] password;
    private final MonitorListener listener;
    private final boolean useTls;
    private final java.nio.file.Path truststorePath;
    private final String truststorePassword;
    private final MessageObserver observer;
    private final Gson gson;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, CompletableFuture<List<TelemetryView>>> telemetryQueries =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<AlertView>>> alertQueries =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<AlertView>> acknowledgementRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<DeviceRegistrationView>> registrationRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<DeviceView>> deviceActionRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<String>> credentialResetRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<AlertView>> resolveRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> passwordRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<AuditEventView>>> auditRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ServerStatusView>> statusRequests =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ServiceCheckView>> serviceCheckRegistrations =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> serviceCheckDeletes =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<ServiceCheckView>>> serviceCheckLists =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<DemoInjectionView>> demoInjections =
            new ConcurrentHashMap<>();

    private volatile Socket socket;
    private volatile JsonLineChannel channel;
    private volatile Thread readerThread;
    private volatile ScheduledExecutorService heartbeatExecutor;

    public MonitorClient(
            String host, int port, String username, char[] password, MonitorListener listener) {
        this(host, port, username, password, listener, false, null, "", null);
    }

    public MonitorClient(
            String host,
            int port,
            String username,
            char[] password,
            MonitorListener listener,
            boolean useTls,
            java.nio.file.Path truststorePath,
            String truststorePassword) {
        this(host, port, username, password, listener,
                useTls, truststorePath, truststorePassword, null);
    }

    public MonitorClient(
            String host,
            int port,
            String username,
            char[] password,
            MonitorListener listener,
            boolean useTls,
            java.nio.file.Path truststorePath,
            String truststorePassword,
            MessageObserver observer) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password.clone();
        this.listener = listener;
        this.useTls = useTls;
        this.truststorePath = truststorePath;
        this.truststorePassword = truststorePassword;
        this.observer = observer;
        this.gson = new GsonBuilder().serializeNulls().create();
    }

    public synchronized void connect() throws IOException, ProtocolException, RemoteErrorException {
        if (channel != null) {
            throw new IllegalStateException("Monitor client is already connected");
        }
        Socket newSocket;
        if (useTls) {
            try {
                javax.net.ssl.SSLContext context =
                        com.shenliwei.devicewatch.common.TlsContextFactory.clientContext(
                                truststorePath, truststorePassword);
                newSocket = context.getSocketFactory().createSocket();
            } catch (java.security.GeneralSecurityException exception) {
                throw new IOException("TLS initialization failed", exception);
            }
        } else {
            newSocket = new Socket();
        }
        newSocket.connect(new InetSocketAddress(host, port), 5_000);
        newSocket.setSoTimeout(10_000);
        JsonLineChannel newChannel = new JsonLineChannel(newSocket, gson, 65_536);
        try {
            authenticate(newChannel);
            newSocket.setSoTimeout(0);
            socket = newSocket;
            channel = newChannel;
            Thread reader = new Thread(this::readLoop, "monitor-reader");
            reader.setDaemon(true);
            readerThread = reader;
            reader.start();
            startHeartbeat();
            listener.onConnectionState(true, "已连接到 " + host + ":" + port);
        } catch (IOException | ProtocolException | RemoteErrorException exception) {
            newSocket.close();
            throw exception;
        }
    }

    public synchronized boolean isConnected() {
        return channel != null && socket != null && !socket.isClosed();
    }

    public CompletableFuture<List<TelemetryView>> queryTelemetry(
            String deviceId, long fromMillis, long toMillis, int limit) {
        CompletableFuture<List<TelemetryView>> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        telemetryQueries.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("deviceId", deviceId);
        payload.addProperty("fromMillis", fromMillis);
        payload.addProperty("toMillis", toMillis);
        payload.addProperty("limit", limit);
        try {
            send(ProtocolMessage.request(
                    "QUERY_TELEMETRY", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            telemetryQueries.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<List<AlertView>> queryAlerts(
            String deviceId,
            String ruleType,
            String status,
            long fromMillis,
            long toMillis,
            int limit) {
        CompletableFuture<List<AlertView>> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        alertQueries.put(requestId, future);
        JsonObject payload = new JsonObject();
        if (deviceId != null && !deviceId.isBlank()) {
            payload.addProperty("deviceId", deviceId);
        }
        if (ruleType != null && !ruleType.isBlank()) {
            payload.addProperty("ruleType", ruleType);
        }
        if (status != null && !status.isBlank()) {
            payload.addProperty("status", status);
        }
        payload.addProperty("fromMillis", fromMillis);
        payload.addProperty("toMillis", toMillis);
        payload.addProperty("limit", limit);
        try {
            send(ProtocolMessage.request(
                    "QUERY_ALERTS", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            alertQueries.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<AlertView> acknowledgeAlert(long alertId) {
        return acknowledgeAlert(alertId, "");
    }

    public CompletableFuture<AlertView> acknowledgeAlert(long alertId, String comment) {
        CompletableFuture<AlertView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        acknowledgementRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("alertId", alertId);
        if (comment != null && !comment.isBlank()) {
            payload.addProperty("comment", comment);
        }
        try {
            send(ProtocolMessage.request(
                    "ACK_ALERT", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            acknowledgementRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<AlertView> resolveAlert(long alertId, String comment) {
        CompletableFuture<AlertView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        resolveRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("alertId", alertId);
        if (comment != null && !comment.isBlank()) {
            payload.addProperty("comment", comment);
        }
        try {
            send(ProtocolMessage.request(
                    "RESOLVE_ALERT", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            resolveRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<DeviceRegistrationView> registerDevice(
            String deviceId,
            String displayName,
            String location,
            DeviceRulesView rules) {
        CompletableFuture<DeviceRegistrationView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        registrationRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("deviceId", deviceId);
        payload.addProperty("displayName", displayName);
        payload.addProperty("location", location);
        payload.add("rules", rulesJson(rules));
        try {
            send(ProtocolMessage.request(
                    "REGISTER_DEVICE", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            registrationRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    /**
     * Registers a device using the server-wide default alert rules.
     */
    public CompletableFuture<DeviceRegistrationView> registerDevice(
            String deviceId, String displayName, String location) {
        CompletableFuture<DeviceRegistrationView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        registrationRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("deviceId", deviceId);
        payload.addProperty("displayName", displayName);
        payload.addProperty("location", location);
        try {
            send(ProtocolMessage.request(
                    "REGISTER_DEVICE", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            registrationRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<DeviceView> updateDevice(
            String deviceId, String displayName, String location) {
        return deviceAction("UPDATE_DEVICE", payload -> {
            payload.addProperty("deviceId", deviceId);
            payload.addProperty("displayName", displayName);
            payload.addProperty("location", location);
        });
    }

    public CompletableFuture<DeviceView> setDeviceEnabled(String deviceId, boolean enabled) {
        return deviceAction("SET_DEVICE_ENABLED", payload -> {
            payload.addProperty("deviceId", deviceId);
            payload.addProperty("enabled", enabled);
        });
    }

    public CompletableFuture<DeviceView> updateDeviceRules(
            String deviceId, DeviceRulesView rules) {
        return deviceAction("UPDATE_DEVICE_RULES", payload -> {
            payload.addProperty("deviceId", deviceId);
            payload.add("rules", rulesJson(rules));
        });
    }

    public CompletableFuture<String> resetDeviceCredential(String deviceId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        credentialResetRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("deviceId", deviceId);
        try {
            send(ProtocolMessage.request(
                    "RESET_DEVICE_CREDENTIAL", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            credentialResetRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<Boolean> changePassword(String oldPassword, String newPassword) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        passwordRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("oldPassword", oldPassword);
        payload.addProperty("newPassword", newPassword);
        try {
            send(ProtocolMessage.request(
                    "CHANGE_PASSWORD", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            passwordRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<List<AuditEventView>> queryAuditLog(int limit) {
        CompletableFuture<List<AuditEventView>> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        auditRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("limit", limit);
        try {
            send(ProtocolMessage.request(
                    "QUERY_AUDIT_LOG", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            auditRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<ServerStatusView> serverStatus() {
        CompletableFuture<ServerStatusView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        statusRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        try {
            send(ProtocolMessage.request(
                    "SERVER_STATUS", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            statusRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<ServiceCheckView> registerServiceCheck(
            String name, String host, int port, int intervalMillis, int failThreshold) {
        CompletableFuture<ServiceCheckView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        serviceCheckRegistrations.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("host", host);
        payload.addProperty("port", port);
        payload.addProperty("intervalMillis", intervalMillis);
        payload.addProperty("failThreshold", failThreshold);
        try {
            send(ProtocolMessage.request(
                    "REGISTER_SERVICE_CHECK", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            serviceCheckRegistrations.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<Void> deleteServiceCheck(long checkId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        serviceCheckDeletes.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("checkId", checkId);
        try {
            send(ProtocolMessage.request(
                    "DELETE_SERVICE_CHECK", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            serviceCheckDeletes.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<List<ServiceCheckView>> listServiceChecks() {
        CompletableFuture<List<ServiceCheckView>> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        serviceCheckLists.put(requestId, future);
        try {
            send(ProtocolMessage.request(
                    "LIST_SERVICE_CHECKS", requestId, System.currentTimeMillis(), new JsonObject()));
        } catch (IOException exception) {
            serviceCheckLists.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    public CompletableFuture<DemoInjectionView> demoInject(
            DemoFault fault, String deviceId, Long checkId) {
        CompletableFuture<DemoInjectionView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        demoInjections.put(requestId, future);
        JsonObject payload = new JsonObject();
        payload.addProperty("fault", fault.name());
        if (deviceId != null) {
            payload.addProperty("deviceId", deviceId);
        }
        if (checkId != null) {
            payload.addProperty("checkId", checkId);
        }
        try {
            send(ProtocolMessage.request(
                    "DEMO_INJECT", requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            demoInjections.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    private CompletableFuture<DeviceView> deviceAction(
            String type, java.util.function.Consumer<JsonObject> fillPayload) {
        CompletableFuture<DeviceView> future = new CompletableFuture<>();
        future.orTimeout(20, TimeUnit.SECONDS);
        String requestId = UUID.randomUUID().toString();
        deviceActionRequests.put(requestId, future);
        JsonObject payload = new JsonObject();
        fillPayload.accept(payload);
        try {
            send(ProtocolMessage.request(
                    type, requestId, System.currentTimeMillis(), payload));
        } catch (IOException exception) {
            deviceActionRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    private static JsonObject rulesJson(DeviceRulesView rules) {
        JsonObject object = new JsonObject();
        object.addProperty("cpuTrigger", rules.cpuTrigger());
        object.addProperty("cpuRecover", rules.cpuRecover());
        object.addProperty("cpuSamples", rules.cpuSamples());
        object.addProperty("memoryTrigger", rules.memoryTrigger());
        object.addProperty("memoryRecover", rules.memoryRecover());
        object.addProperty("memorySamples", rules.memorySamples());
        object.addProperty("tempTrigger", rules.temperatureTrigger());
        object.addProperty("tempRecover", rules.temperatureRecover());
        return object;
    }

    private void authenticate(JsonLineChannel newChannel)
            throws IOException, ProtocolException, RemoteErrorException {
        JsonObject payload = new JsonObject();
        payload.addProperty("role", "MONITOR");
        payload.addProperty("username", username);
        payload.addProperty("password", new String(password));
        String requestId = UUID.randomUUID().toString();
        JsonObject request = ProtocolMessage.request(
                "AUTH", requestId, System.currentTimeMillis(), payload);
        observe(MessageObserver.Direction.SENT, request);
        newChannel.write(request);
        JsonObject response = newChannel.read();
        if (response == null) {
            throw new IOException("Server closed the connection during authentication");
        }
        observe(MessageObserver.Direction.RECEIVED, response);
        if (!requestId.equals(response.get("requestId").getAsString())) {
            throw ProtocolException.validation("Authentication response requestId did not match");
        }
        String type = response.get("type").getAsString();
        if ("ERROR".equals(type)) {
            JsonObject error = response.getAsJsonObject("payload");
            throw new RemoteErrorException(
                    error.get("code").getAsString(), error.get("message").getAsString());
        }
        if (!"AUTH_OK".equals(type)) {
            throw ProtocolException.validation("Expected AUTH_OK, received " + type);
        }
    }

    private void startHeartbeat() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "monitor-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor = executor;
        executor.scheduleAtFixedRate(() -> {
            try {
                JsonObject payload = new JsonObject();
                send(ProtocolMessage.request(
                        "HEARTBEAT",
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        payload));
            } catch (IOException exception) {
                disconnectFromReader("心跳发送失败", exception);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void readLoop() {
        try {
            JsonLineChannel current = channel;
            while (!closed.get() && current != null) {
                JsonObject message = current.read();
                if (message == null) {
                    break;
                }
                observe(MessageObserver.Direction.RECEIVED, message);
                dispatch(message);
            }
            if (!closed.get()) {
                listener.onConnectionState(false, "服务器已断开连接");
            }
        } catch (IOException | ProtocolException exception) {
            disconnectFromReader("网络连接中断", exception);
        } finally {
            closeConnection();
        }
    }

    private void dispatch(JsonObject message) {
        String type = message.get("type").getAsString();
        String requestId = message.get("requestId").getAsString();
        JsonObject payload = message.getAsJsonObject("payload");
        switch (type) {
            case "DEVICE_SNAPSHOT" -> listener.onDeviceSnapshot(parseDevices(payload));
            case "DEVICE_UPDATE" -> listener.onDeviceUpdate(parseDevice(payload));
            case "ALERT_SNAPSHOT" -> listener.onAlertSnapshot(parseAlerts(payload));
            case "ALERT_UPDATE" -> listener.onAlertUpdate(parseAlert(payload));
            case "ALERT_ACKED" -> completeAcknowledgement(requestId, parseAlert(payload));
            case "TELEMETRY_HISTORY" -> completeTelemetryQuery(requestId, payload);
            case "ALERT_HISTORY" -> completeAlertQuery(requestId, payload);
            case "DEVICE_REGISTERED" -> completeRegistration(requestId, payload);
            case "DEVICE_UPDATED" -> completeDeviceAction(requestId, payload);
            case "DEVICE_CREDENTIAL_RESET" -> completeCredentialReset(requestId, payload);
            case "ALERT_RESOLVED" -> completeResolve(requestId, payload);
            case "PASSWORD_CHANGED" -> completePasswordChange(requestId);
            case "AUDIT_LOG" -> completeAuditQuery(requestId, payload);
            case "SERVER_STATUS" -> completeStatusQuery(requestId, payload);
            case "SERVICE_CHECK_REGISTERED" ->
                    completeServiceCheckRegistration(requestId, payload);
            case "SERVICE_CHECK_DELETED" ->
                    completeServiceCheckDeletion(requestId, payload);
            case "SERVICE_CHECK_SNAPSHOT" -> {
                List<ServiceCheckView> checks = parseServiceChecks(payload);
                CompletableFuture<List<ServiceCheckView>> future = serviceCheckLists.remove(requestId);
                if (future != null) {
                    future.complete(checks);
                }
                listener.onServiceCheckSnapshot(checks);
            }
            case "SERVICE_CHECK_UPDATE" -> {
                if (payload.has("deleted")) {
                    listener.onServiceCheckDeleted(payload.get("checkId").getAsLong());
                } else {
                    listener.onServiceCheckUpdate(parseServiceCheck(payload));
                }
            }
            case "DEMO_INJECTED" -> completeDemoInjection(requestId, payload);
            case "ERROR" -> {
                String code = string(payload, "code");
                String detail = string(payload, "message");
                listener.onRemoteError(code, detail);
                completePendingQueryWithError(requestId, code, detail);
            }
            case "HEARTBEAT_ACK", "AUTH_OK" -> {
                // No UI action is required.
            }
            default -> listener.onRemoteError("UNKNOWN_MESSAGE", "Unknown server message: " + type);
        }
    }

    private void completeTelemetryQuery(String requestId, JsonObject payload) {
        CompletableFuture<List<TelemetryView>> future = telemetryQueries.remove(requestId);
        if (future == null) {
            return;
        }
        List<TelemetryView> samples = new ArrayList<>();
        for (JsonElement element : payload.getAsJsonArray("samples")) {
            JsonObject sample = element.getAsJsonObject();
            samples.add(new TelemetryView(
                    string(sample, "deviceId"),
                    sample.get("collectedAt").getAsLong(),
                    sample.get("receivedAt").getAsLong(),
                    sample.get("cpuPercent").getAsDouble(),
                    sample.get("memoryPercent").getAsDouble(),
                    sample.get("temperatureCelsius").getAsDouble(),
                    nullableDouble(sample, "diskPercent")));
        }
        future.complete(List.copyOf(samples));
    }

    private void completeRegistration(String requestId, JsonObject payload) {
        CompletableFuture<DeviceRegistrationView> future = registrationRequests.remove(requestId);
        if (future != null) {
            future.complete(new DeviceRegistrationView(
                    parseDevice(payload), string(payload, "credential")));
        }
    }

    private void completeDeviceAction(String requestId, JsonObject payload) {
        CompletableFuture<DeviceView> future = deviceActionRequests.remove(requestId);
        if (future != null) {
            future.complete(parseDevice(payload));
        }
    }

    private void completeCredentialReset(String requestId, JsonObject payload) {
        CompletableFuture<String> future = credentialResetRequests.remove(requestId);
        if (future != null) {
            future.complete(string(payload, "credential"));
        }
    }

    private void completeResolve(String requestId, JsonObject payload) {
        CompletableFuture<AlertView> future = resolveRequests.remove(requestId);
        if (future != null) {
            future.complete(parseAlert(payload));
        }
    }

    private void completePasswordChange(String requestId) {
        CompletableFuture<Boolean> future = passwordRequests.remove(requestId);
        if (future != null) {
            future.complete(true);
        }
    }

    private void completeAuditQuery(String requestId, JsonObject payload) {
        CompletableFuture<List<AuditEventView>> future = auditRequests.remove(requestId);
        if (future == null) {
            return;
        }
        List<AuditEventView> events = new ArrayList<>();
        JsonArray array = payload.getAsJsonArray("events");
        if (array != null) {
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                events.add(new AuditEventView(
                        object.get("id").getAsLong(),
                        string(object, "actor"),
                        string(object, "action"),
                        string(object, "target"),
                        string(object, "detail"),
                        object.get("createdAt").getAsLong()));
            }
        }
        future.complete(List.copyOf(events));
    }

    private void completeStatusQuery(String requestId, JsonObject payload) {
        CompletableFuture<ServerStatusView> future = statusRequests.remove(requestId);
        if (future != null) {
            future.complete(new ServerStatusView(
                    string(payload, "host"),
                    payload.get("port").getAsInt(),
                    payload.get("startedAt").getAsLong(),
                    payload.get("uptimeMillis").getAsLong(),
                    payload.get("deviceSessions").getAsInt(),
                    payload.get("monitorSessions").getAsInt(),
                    payload.get("registeredDevices").getAsInt(),
                    payload.get("telemetryCount").getAsLong(),
                    payload.get("activeAlerts").getAsLong(),
                    string(payload, "databasePath"),
                    payload.get("databaseSizeBytes").getAsLong(),
                    payload.has("tlsEnabled") && payload.get("tlsEnabled").getAsBoolean(),
                    payload.has("tlsPort") ? payload.get("tlsPort").getAsInt() : 0,
                    payload.has("tlsCertSubject")
                            ? payload.get("tlsCertSubject").getAsString()
                            : "",
                    payload.has("tlsHandshakeFailures")
                            ? payload.get("tlsHandshakeFailures").getAsLong()
                            : 0));
        }
    }

    private void completeServiceCheckRegistration(String requestId, JsonObject payload) {
        CompletableFuture<ServiceCheckView> future = serviceCheckRegistrations.remove(requestId);
        if (future != null) {
            future.complete(parseServiceCheck(payload));
        }
    }

    private void completeServiceCheckDeletion(String requestId, JsonObject payload) {
        CompletableFuture<Void> future = serviceCheckDeletes.remove(requestId);
        if (future != null) {
            future.complete(null);
        }
    }

    private void completeDemoInjection(String requestId, JsonObject payload) {
        CompletableFuture<DemoInjectionView> future = demoInjections.remove(requestId);
        if (future != null) {
            future.complete(parseDemoInjection(payload));
        }
    }

    private static DemoInjectionView parseDemoInjection(JsonObject object) {
        return new DemoInjectionView(
                string(object, "fault"),
                object.has("deviceId") && !object.get("deviceId").isJsonNull()
                        ? object.get("deviceId").getAsString()
                        : null,
                object.has("checkId") && !object.get("checkId").isJsonNull()
                        ? object.get("checkId").getAsLong()
                        : null);
    }

    private List<ServiceCheckView> parseServiceChecks(JsonObject payload) {
        List<ServiceCheckView> checks = new ArrayList<>();
        JsonArray array = payload.getAsJsonArray("checks");
        if (array == null) {
            return List.of();
        }
        for (JsonElement element : array) {
            checks.add(parseServiceCheck(element.getAsJsonObject()));
        }
        return List.copyOf(checks);
    }

    private static ServiceCheckView parseServiceCheck(JsonObject object) {
        return new ServiceCheckView(
                object.get("checkId").getAsLong(),
                string(object, "name"),
                string(object, "host"),
                object.get("port").getAsInt(),
                object.get("intervalMillis").getAsInt(),
                object.get("failThreshold").getAsInt(),
                object.get("enabled").getAsBoolean(),
                object.get("online").getAsBoolean(),
                object.get("consecutiveFailures").getAsInt(),
                object.get("lastCheckedAt").getAsLong(),
                object.get("lastDurationMillis").getAsLong(),
                object.get("availabilityPercent").getAsDouble());
    }

    private void completeAlertQuery(String requestId, JsonObject payload) {
        CompletableFuture<List<AlertView>> future = alertQueries.remove(requestId);
        if (future != null) {
            future.complete(parseAlerts(payload));
        }
    }

    private void completePendingQueryWithError(String requestId, String code, String message) {
        RemoteErrorException error = new RemoteErrorException(code, message);
        CompletableFuture<List<TelemetryView>> telemetryFuture = telemetryQueries.remove(requestId);
        if (telemetryFuture != null) {
            telemetryFuture.completeExceptionally(error);
        }
        CompletableFuture<List<AlertView>> alertFuture = alertQueries.remove(requestId);
        if (alertFuture != null) {
            alertFuture.completeExceptionally(error);
        }
        CompletableFuture<AlertView> acknowledgement = acknowledgementRequests.remove(requestId);
        if (acknowledgement != null) {
            acknowledgement.completeExceptionally(error);
        }
        failIfPresent(registrationRequests.remove(requestId), error);
        failIfPresent(deviceActionRequests.remove(requestId), error);
        failIfPresent(credentialResetRequests.remove(requestId), error);
        failIfPresent(resolveRequests.remove(requestId), error);
        failIfPresent(passwordRequests.remove(requestId), error);
        failIfPresent(auditRequests.remove(requestId), error);
        failIfPresent(statusRequests.remove(requestId), error);
        failIfPresent(serviceCheckRegistrations.remove(requestId), error);
        failIfPresent(serviceCheckDeletes.remove(requestId), error);
        failIfPresent(serviceCheckLists.remove(requestId), error);
        failIfPresent(demoInjections.remove(requestId), error);
    }

    private static void failIfPresent(CompletableFuture<?> future, Exception error) {
        if (future != null) {
            future.completeExceptionally(error);
        }
    }

    private void completeAcknowledgement(String requestId, AlertView alert) {
        CompletableFuture<AlertView> future = acknowledgementRequests.remove(requestId);
        if (future != null) {
            future.complete(alert);
        }
    }

    private List<DeviceView> parseDevices(JsonObject payload) {
        List<DeviceView> devices = new ArrayList<>();
        JsonArray array = payload.getAsJsonArray("devices");
        if (array == null) {
            return List.of();
        }
        for (JsonElement element : array) {
            devices.add(parseDevice(element.getAsJsonObject()));
        }
        devices.sort(Comparator.comparing(DeviceView::deviceId));
        return List.copyOf(devices);
    }

    private static DeviceView parseDevice(JsonObject object) {
        JsonObject rulesObject = object.has("rules") && !object.get("rules").isJsonNull()
                ? object.getAsJsonObject("rules")
                : new JsonObject();
        return new DeviceView(
                string(object, "deviceId"),
                string(object, "displayName"),
                string(object, "location"),
                object.has("enabled") && object.get("enabled").getAsBoolean(),
                object.get("online").getAsBoolean(),
                object.get("lastSeenAt").getAsLong(),
                object.has("createdAt") ? object.get("createdAt").getAsLong() : 0,
                nullableDouble(object, "cpuPercent"),
                nullableDouble(object, "memoryPercent"),
                nullableDouble(object, "temperatureCelsius"),
                nullableDouble(object, "diskPercent"),
                new DeviceRulesView(
                        rulesObject.get("cpuTrigger").getAsDouble(),
                        rulesObject.get("cpuRecover").getAsDouble(),
                        rulesObject.get("cpuSamples").getAsInt(),
                        rulesObject.get("memoryTrigger").getAsDouble(),
                        rulesObject.get("memoryRecover").getAsDouble(),
                        rulesObject.get("memorySamples").getAsInt(),
                        rulesObject.get("tempTrigger").getAsDouble(),
                        rulesObject.get("tempRecover").getAsDouble()),
                object.has("latencyMillis") ? object.get("latencyMillis").getAsLong() : 0L,
                object.has("networkKbps") ? object.get("networkKbps").getAsDouble() : 0.0,
                object.has("real") && object.get("real").getAsBoolean());
    }

    private List<AlertView> parseAlerts(JsonObject payload) {
        List<AlertView> alerts = new ArrayList<>();
        JsonArray array = payload.getAsJsonArray("alerts");
        if (array == null) {
            return List.of();
        }
        for (JsonElement element : array) {
            alerts.add(parseAlert(element.getAsJsonObject()));
        }
        alerts.sort(Comparator.comparingLong(AlertView::triggeredAt).reversed());
        return List.copyOf(alerts);
    }

    private static AlertView parseAlert(JsonObject object) {
        JsonElement resolvedElement = object.get("resolvedAt");
        Long resolvedAt = resolvedElement == null
                        || resolvedElement instanceof JsonNull
                        || resolvedElement.isJsonNull()
                ? null
                : resolvedElement.getAsLong();
        JsonElement acknowledgedElement = object.get("acknowledgedAt");
        Long acknowledgedAt = acknowledgedElement == null
                        || acknowledgedElement instanceof JsonNull
                        || acknowledgedElement.isJsonNull()
                ? null
                : acknowledgedElement.getAsLong();
        return new AlertView(
                object.get("alertId").getAsLong(),
                string(object, "deviceId"),
                string(object, "ruleType"),
                string(object, "severity"),
                string(object, "status"),
                nullableDouble(object, "triggerValue"),
                object.get("triggeredAt").getAsLong(),
                resolvedAt,
                acknowledgedAt,
                nullableString(object, "acknowledgedBy"),
                nullableString(object, "ackComment"));
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static Double nullableDouble(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsDouble();
    }

    private static String nullableString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private void send(JsonObject message) throws IOException {
        JsonLineChannel current = channel;
        if (current == null) {
            throw new IOException("Monitor client is not connected");
        }
        observe(MessageObserver.Direction.SENT, message);
        current.write(message);
    }

    private void observe(MessageObserver.Direction direction, JsonObject message) {
        MessageObserver currentObserver = observer;
        if (currentObserver == null) {
            return;
        }
        String type = message.has("type") && message.get("type").isJsonPrimitive()
                ? message.get("type").getAsString()
                : "unknown";
        int bytes = gson.toJson(message)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        currentObserver.onMessage(direction, type, bytes, System.currentTimeMillis());
    }

    private void disconnectFromReader(String detail, Exception exception) {
        if (!closed.get()) {
            listener.onConnectionState(false, detail + ": " + exception.getMessage());
        }
        closeConnection();
    }

    private synchronized void closeConnection() {
        JsonLineChannel currentChannel = channel;
        Socket currentSocket = socket;
        channel = null;
        socket = null;
        if (currentChannel != null) {
            try {
                currentChannel.close();
            } catch (IOException ignored) {
                // Socket may already be closed.
            }
        } else if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
                // Socket may already be closed.
            }
        }
        ScheduledExecutorService executor = heartbeatExecutor;
        heartbeatExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        IOException failure = new IOException("Connection closed before response");
        telemetryQueries.values().forEach(future -> future.completeExceptionally(failure));
        alertQueries.values().forEach(future -> future.completeExceptionally(failure));
        acknowledgementRequests.values().forEach(future -> future.completeExceptionally(failure));
        registrationRequests.values().forEach(future -> future.completeExceptionally(failure));
        deviceActionRequests.values().forEach(future -> future.completeExceptionally(failure));
        credentialResetRequests.values().forEach(future -> future.completeExceptionally(failure));
        resolveRequests.values().forEach(future -> future.completeExceptionally(failure));
        passwordRequests.values().forEach(future -> future.completeExceptionally(failure));
        auditRequests.values().forEach(future -> future.completeExceptionally(failure));
        statusRequests.values().forEach(future -> future.completeExceptionally(failure));
        serviceCheckRegistrations.values().forEach(future -> future.completeExceptionally(failure));
        serviceCheckDeletes.values().forEach(future -> future.completeExceptionally(failure));
        serviceCheckLists.values().forEach(future -> future.completeExceptionally(failure));
        demoInjections.values().forEach(future -> future.completeExceptionally(failure));
        telemetryQueries.clear();
        alertQueries.clear();
        acknowledgementRequests.clear();
        registrationRequests.clear();
        deviceActionRequests.clear();
        credentialResetRequests.clear();
        resolveRequests.clear();
        passwordRequests.clear();
        auditRequests.clear();
        statusRequests.clear();
        serviceCheckRegistrations.clear();
        serviceCheckDeletes.clear();
        serviceCheckLists.clear();
        demoInjections.clear();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        JsonLineChannel current = channel;
        if (current != null) {
            try {
                current.write(ProtocolMessage.request(
                        "LOGOUT",
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        new JsonObject()));
            } catch (IOException ignored) {
                // The connection may already be unavailable.
            }
        }
        closeConnection();
        java.util.Arrays.fill(password, '\0');
        listener.onConnectionState(false, "连接已关闭");
    }
}

