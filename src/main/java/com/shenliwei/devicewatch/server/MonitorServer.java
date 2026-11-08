package com.shenliwei.devicewatch.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.shenliwei.devicewatch.alert.AlertAction;
import com.shenliwei.devicewatch.alert.AlertEngine;
import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.domain.AlertRecord;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.domain.AlertSeverity;
import com.shenliwei.devicewatch.domain.AlertStatus;
import com.shenliwei.devicewatch.domain.DeviceInfo;
import com.shenliwei.devicewatch.domain.DeviceRules;
import com.shenliwei.devicewatch.domain.Role;
import com.shenliwei.devicewatch.domain.ServiceCheck;
import com.shenliwei.devicewatch.domain.TelemetrySample;
import com.shenliwei.devicewatch.persistence.AlertChange;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.protocol.JsonLineChannel;
import com.shenliwei.devicewatch.protocol.ProtocolException;
import com.shenliwei.devicewatch.protocol.ProtocolMessage;
import com.shenliwei.devicewatch.protocol.ProtocolValidator;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP server for device telemetry and monitor clients.
 */
public final class MonitorServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MonitorServer.class.getName());
    private static final long MONITOR_INACTIVITY_TIMEOUT_MILLIS = 30_000;
    private static final long DEVICE_CLOCK_FUTURE_TOLERANCE_MILLIS = 300_000;

    private final AppConfig config;
    private final Database database;
    private final Gson gson;
    private final AlertEngine alertEngine;
    private final long startedAtMillis;
    private final Map<String, DeviceState> deviceStates = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> deviceSessions = new ConcurrentHashMap<>();
    private final Set<ClientSession> monitorSessions = ConcurrentHashMap.newKeySet();
    private final Map<Long, ServiceCheckState> serviceChecks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final ExecutorService connectionPool;
    private final ScheduledExecutorService maintenanceExecutor;
    private long lastRetentionRunAt;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private javax.net.ssl.SSLServerSocket tlsServerSocket;
    private Thread tlsAcceptThread;
    private final java.util.concurrent.atomic.AtomicLong tlsHandshakeFailureCount =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile String tlsCertSubject = "";

    public MonitorServer(AppConfig config, Database database) {
        this.config = config;
        this.database = database;
        this.gson = new GsonBuilder().serializeNulls().create();
        this.alertEngine = new AlertEngine();
        this.startedAtMillis = System.currentTimeMillis();
        this.connectionPool = Executors.newFixedThreadPool(64, namedThreads("net-worker"));
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(
                namedThreads("maintenance"));
    }

    public synchronized void start() throws IOException, SQLException {
        if (running.get()) {
            throw new IllegalStateException("Server is already running");
        }

        database.initialize();
        loadInitialState();

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.host(), config.port()));
        running.set(true);

        acceptThread = new Thread(this::acceptLoop, "tcp-acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
        if (config.tlsEnabled()) {
            startTlsListener();
        }
        maintenanceExecutor.scheduleAtFixedRate(
                this::runMaintenance, 250, 250, TimeUnit.MILLISECONDS);

        LOGGER.info(() -> "Device monitor server listening on "
                + config.host()
                + ":"
                + port());
    }

    private void startTlsListener() throws IOException {
        try {
            tlsCertSubject = com.shenliwei.devicewatch.common.TlsContextFactory
                    .firstCertSubject(
                            config.tlsKeystorePath(), config.tlsKeystorePassword());
            javax.net.ssl.SSLServerSocketFactory factory =
                    com.shenliwei.devicewatch.common.TlsContextFactory.serverFactory(
                            config.tlsKeystorePath(), config.tlsKeystorePassword());
            javax.net.ssl.SSLServerSocket tlsSocket =
                    (javax.net.ssl.SSLServerSocket) factory.createServerSocket();
            tlsSocket.setReuseAddress(true);
            tlsSocket.bind(new InetSocketAddress(config.host(), config.tlsPort()));
            tlsServerSocket = tlsSocket;
            tlsAcceptThread = new Thread(this::acceptTlsLoop, "tls-acceptor");
            tlsAcceptThread.setDaemon(true);
            tlsAcceptThread.start();
            LOGGER.info(() -> "TLS listener started on " + config.host()
                    + ":" + tlsSocket.getLocalPort()
                    + " cert=" + tlsCertSubject);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IOException("Failed to initialize the TLS listener", exception);
        }
    }

    public int port() {
        ServerSocket current = serverSocket;
        if (current == null) {
            throw new IllegalStateException("Server has not been started");
        }
        return current.getLocalPort();
    }

    public boolean tlsEnabled() {
        return config.tlsEnabled();
    }

    public int tlsLocalPort() {
        javax.net.ssl.SSLServerSocket current = tlsServerSocket;
        if (current == null) {
            throw new IllegalStateException("TLS listener has not been started");
        }
        return current.getLocalPort();
    }

    public long tlsHandshakeFailures() {
        return tlsHandshakeFailureCount.get();
    }

    public String tlsCertSubject() {
        return tlsCertSubject;
    }

    public void awaitTermination() throws InterruptedException {
        stopped.await();
    }

    private void loadInitialState() throws SQLException {
        List<DeviceInfo> devices = database.listDeviceInfos(defaultRules());
        Map<String, TelemetrySample> latest = database.latestTelemetryByDevice();
        for (DeviceInfo device : devices) {
            DeviceState state = DeviceState.from(device);
            TelemetrySample sample = latest.get(device.deviceId());
            if (sample != null) {
                state.applyHistoricalSample(sample);
            }
            deviceStates.put(device.deviceId(), state);
        }
        for (AlertRecord alert : database.activeAlerts()) {
            DeviceState state = deviceStates.get(alert.deviceId());
            if (state != null) {
                state.putActiveAlert(alert);
            }
        }
        for (ServiceCheck check : database.listServiceChecks()) {
            if (check.enabled()) {
                serviceChecks.put(check.id(), new ServiceCheckState(check));
            }
        }
    }

    private DeviceRules defaultRules() {
        return new DeviceRules(
                config.cpuTriggerThreshold(),
                config.cpuRecoverThreshold(),
                config.cpuRequiredConsecutiveSamples(),
                config.memoryTriggerThreshold(),
                config.memoryRecoverThreshold(),
                config.memoryRequiredConsecutiveSamples(),
                config.temperatureTriggerThreshold(),
                config.temperatureRecoverThreshold());
    }

    private static AlertSeverity severityFor(
            AlertRuleType ruleType, Double value, DeviceRules rules) {
        double trigger = switch (ruleType) {
            case CPU -> rules.cpuTrigger();
            case MEMORY -> rules.memoryTrigger();
            case TEMPERATURE -> rules.temperatureTrigger();
            case OFFLINE, SERVICE_DOWN -> 0;
        };
        if (ruleType == AlertRuleType.OFFLINE || ruleType == AlertRuleType.SERVICE_DOWN) {
            return AlertSeverity.WARNING;
        }
        return value != null && value >= trigger + 10
                ? AlertSeverity.CRITICAL
                : AlertSeverity.WARNING;
    }

    private void acceptLoop() {
        acceptLoop(serverSocket, false);
    }

    private void acceptTlsLoop() {
        acceptLoop(tlsServerSocket, true);
    }

    private void acceptLoop(java.net.ServerSocket listeningSocket, boolean tls) {
        while (running.get()) {
            try {
                Socket socket = listeningSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                if (tls) {
                    socket.setSoTimeout(config.authTimeoutMillis());
                    try {
                        ((javax.net.ssl.SSLSocket) socket).startHandshake();
                    } catch (IOException exception) {
                        tlsHandshakeFailureCount.incrementAndGet();
                        LOGGER.log(Level.WARNING, "TLS handshake rejected", exception);
                        socket.close();
                        continue;
                    }
                }
                ClientSession session = new ClientSession(socket);
                connectionPool.execute(() -> serve(session));
            } catch (IOException exception) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Failed to accept a client connection", exception);
                }
            }
        }
    }

    private void serve(ClientSession session) {
        JsonLineChannel channel = null;
        try {
            session.socket.setSoTimeout(config.authTimeoutMillis());
            channel = new JsonLineChannel(session.socket, gson, config.maxMessageBytes());
            session.channel = channel;
            while (running.get() && !session.closed.get()) {
                JsonObject message;
                try {
                    message = channel.read();
                } catch (SocketTimeoutException timeout) {
                    if (!session.authenticated) {
                        sendError(
                                session,
                                "unauthenticated",
                                "AUTH_FAILED",
                                "Authentication timed out",
                                false);
                    }
                    break;
                }
                if (message == null) {
                    if (!session.authenticated
                            && session.socket instanceof javax.net.ssl.SSLSocket) {
                        tlsHandshakeFailureCount.incrementAndGet();
                    }
                    break;
                }
                session.touch();
                handleMessage(session, message);
            }
        } catch (ProtocolException exception) {
            sendError(session, "protocol-error", exception.code(), exception.getMessage(), !exception.fatal());
            if (exception.fatal()) {
                session.close();
            }
        } catch (IOException exception) {
            if (!session.authenticated
                    && exception instanceof javax.net.ssl.SSLException) {
                tlsHandshakeFailureCount.incrementAndGet();
            }
            if (running.get() && !session.closed.get()) {
                LOGGER.log(Level.FINE, "Client connection ended: " + session.remoteAddress(), exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Unexpected client handler failure", exception);
        } finally {
            unregister(session);
            session.close();
        }
    }

    private void handleMessage(ClientSession session, JsonObject message) throws IOException {
        String requestId;
        String type;
        JsonObject payload;
        try {
            requestId = ProtocolValidator.requiredRequestId(message);
            ProtocolValidator.requiredLong(message, "timestamp");
            type = ProtocolValidator.requiredType(message);
            JsonElement payloadElement = message.get("payload");
            if (payloadElement == null || !payloadElement.isJsonObject()) {
                throw ProtocolException.validation("payload must be an object");
            }
            payload = payloadElement.getAsJsonObject();
        } catch (ProtocolException exception) {
            sendError(session, "invalid-message", exception.code(), exception.getMessage(), true);
            return;
        }

        if (!session.authenticated) {
            if (!"AUTH".equals(type)) {
                sendError(session, requestId, "AUTH_REQUIRED", "AUTH is required first", true);
                return;
            }
            authenticate(session, requestId, payload);
            return;
        }

        if (session.role == Role.DEVICE) {
            handleDeviceMessage(session, requestId, type, payload);
        } else {
            handleMonitorMessage(session, requestId, type, payload);
        }
    }

    private void authenticate(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            String roleValue = ProtocolValidator.requiredString(payload, "role", 16);
            Role role;
            try {
                role = Role.valueOf(roleValue);
            } catch (IllegalArgumentException exception) {
                throw ProtocolException.validation("role must be DEVICE or MONITOR");
            }

            boolean authenticated;
            String identity;
            if (role == Role.DEVICE) {
                identity = ProtocolValidator.requiredString(payload, "deviceId", 64);
                char[] credential =
                        ProtocolValidator.requiredString(payload, "credential", 256).toCharArray();
                try {
                    authenticated = database.authenticateDevice(identity, credential);
                } catch (SQLException exception) {
                    LOGGER.log(Level.SEVERE, "Device authentication storage failure", exception);
                    sendError(session, requestId, "INTERNAL_ERROR", "Authentication unavailable", true);
                    return;
                } finally {
                    java.util.Arrays.fill(credential, '\0');
                }
            } else {
                identity = ProtocolValidator.requiredString(payload, "username", 64);
                char[] password =
                        ProtocolValidator.requiredString(payload, "password", 256).toCharArray();
                try {
                    authenticated = database.authenticateOperator(identity, password);
                } catch (SQLException exception) {
                    LOGGER.log(Level.SEVERE, "Operator authentication storage failure", exception);
                    sendError(session, requestId, "INTERNAL_ERROR", "Authentication unavailable", true);
                    return;
                } finally {
                    java.util.Arrays.fill(password, '\0');
                }
            }

            if (!authenticated) {
                session.authAttempts++;
                LOGGER.warning(() -> "Authentication failed for role="
                        + role
                        + " identity="
                        + identity
                        + " attempt="
                        + session.authAttempts);
                boolean exhausted = session.authAttempts >= 5;
                sendError(
                        session,
                        requestId,
                        exhausted ? "AUTH_ATTEMPTS_EXCEEDED" : "AUTH_FAILED",
                        "Invalid credentials",
                        !exhausted);
                if (exhausted) {
                    session.close();
                }
                return;
            }

            session.authenticated = true;
            session.role = role;
            session.identity = identity;
            session.sessionId = UUID.randomUUID().toString();
            session.socket.setSoTimeout(0);
            session.touch();
            audit(identity, "LOGIN", role.name(), "Authentication succeeded");

            long now = System.currentTimeMillis();
            JsonObject authPayload = new JsonObject();
            authPayload.addProperty("role", role.name());
            authPayload.addProperty("sessionId", session.sessionId);
            authPayload.addProperty("serverTime", now);
            send(session, ProtocolMessage.response("AUTH_OK", requestId, now, authPayload));

            if (role == Role.DEVICE) {
                boolean real = payload.has("real") && payload.get("real").getAsBoolean();
                registerDevice(session, now, real);
            } else {
                monitorSessions.add(session);
                sendDeviceSnapshot(session);
                sendAlertSnapshot(session);
            }
        } catch (ProtocolException exception) {
            session.authAttempts++;
            sendError(session, requestId, exception.code(), exception.getMessage(), true);
            if (session.authAttempts >= 5) {
                session.close();
            }
        }
    }

    private void registerDevice(ClientSession session, long now, boolean real) throws IOException {
        ClientSession previous = deviceSessions.put(session.identity, session);
        if (previous != null && previous != session) {
            sendError(
                    previous,
                    "session-replaced",
                    "SESSION_REPLACED",
                    "A newer session authenticated for this device",
                    false);
            previous.close();
        }

        DeviceState state = deviceStates.get(session.identity);
        LOGGER.info(() -> "Device session authenticated: " + session.identity);
        JsonObject update;
        synchronized (state) {
            state.online(true);
            state.real(real);
            state.lastSeenAt(now);
            AlertRecord offlineAlert = state.activeAlert(AlertRuleType.OFFLINE);
            if (offlineAlert != null) {
                try {
                    AlertRecord resolved = database.resolveAlert(offlineAlert.alertId(), now);
                    state.removeActiveAlert(AlertRuleType.OFFLINE);
                    broadcastAlert(resolved);
                } catch (SQLException exception) {
                    LOGGER.log(Level.SEVERE, "Failed to resolve offline alert", exception);
                }
            }
            update = deviceUpdate(state);
        }
        broadcastMonitor(update);
    }

    private void handleDeviceMessage(
            ClientSession session, String requestId, String type, JsonObject payload)
            throws IOException {
        switch (type) {
            case "HEARTBEAT" -> handleHeartbeat(session, requestId, true);
            case "TELEMETRY" -> handleTelemetry(session, requestId, payload);
            case "LOGOUT" -> session.close();
            default -> sendError(
                    session, requestId, "ROLE_FORBIDDEN", "Message is not allowed for a device", true);
        }
    }

    private void handleMonitorMessage(
            ClientSession session, String requestId, String type, JsonObject payload)
            throws IOException {
        switch (type) {
            case "HEARTBEAT" -> handleHeartbeat(session, requestId, false);
            case "QUERY_TELEMETRY" -> queryTelemetry(session, requestId, payload);
            case "QUERY_ALERTS" -> queryAlerts(session, requestId, payload);
            case "ACK_ALERT" -> acknowledgeAlert(session, requestId, payload);
            case "RESOLVE_ALERT" -> resolveAlertMessage(session, requestId, payload);
            case "REGISTER_DEVICE" -> registerDeviceMessage(session, requestId, payload);
            case "UPDATE_DEVICE" -> updateDeviceMessage(session, requestId, payload);
            case "SET_DEVICE_ENABLED" -> setDeviceEnabledMessage(session, requestId, payload);
            case "UPDATE_DEVICE_RULES" -> updateDeviceRulesMessage(session, requestId, payload);
            case "RESET_DEVICE_CREDENTIAL" -> resetDeviceCredentialMessage(session, requestId, payload);
            case "CHANGE_PASSWORD" -> changePasswordMessage(session, requestId, payload);
            case "QUERY_AUDIT_LOG" -> queryAuditLogMessage(session, requestId, payload);
            case "SERVER_STATUS" -> serverStatusMessage(session, requestId, payload);
            case "REGISTER_SERVICE_CHECK" ->
                    registerServiceCheckMessage(session, requestId, payload);
            case "DELETE_SERVICE_CHECK" ->
                    deleteServiceCheckMessage(session, requestId, payload);
            case "LIST_SERVICE_CHECKS" ->
                    listServiceChecksMessage(session, requestId, payload);
            case "DEMO_INJECT" -> demoInjectMessage(session, requestId, payload);
            case "LOGOUT" -> session.close();
            default -> sendError(
                    session,
                    requestId,
                    "ROLE_FORBIDDEN",
                    "Message is not allowed for a monitor",
                    true);
        }
    }

    private void handleHeartbeat(ClientSession session, String requestId, boolean device)
            throws IOException {
        long now = System.currentTimeMillis();
        if (device) {
            DeviceState state = deviceStates.get(session.identity);
            synchronized (state) {
                state.lastSeenAt(now);
                state.online(true);
            }
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("serverTime", now);
        send(session, ProtocolMessage.response("HEARTBEAT_ACK", requestId, now, payload));
    }

    private void handleTelemetry(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            long collectedAt = ProtocolValidator.requiredLong(payload, "collectedAt");
            double cpu = ProtocolValidator.requiredDouble(payload, "cpuPercent");
            double memory = ProtocolValidator.requiredDouble(payload, "memoryPercent");
            double temperature =
                    ProtocolValidator.requiredDouble(payload, "temperatureCelsius");
            Double disk = ProtocolValidator.optionalDouble(payload, "diskPercent");
            long receivedAt = System.currentTimeMillis();
            if (collectedAt > receivedAt + DEVICE_CLOCK_FUTURE_TOLERANCE_MILLIS) {
                throw ProtocolException.validation(
                        "collectedAt is too far in the future");
            }
            if (cpu < 0 || cpu > 100) {
                throw ProtocolException.validation("cpuPercent must be between 0 and 100");
            }
            if (memory < 0 || memory > 100) {
                throw ProtocolException.validation("memoryPercent must be between 0 and 100");
            }
            if (temperature < -40 || temperature > 125) {
                throw ProtocolException.validation("temperatureCelsius must be between -40 and 125");
            }
            if (disk != null && (disk < 0 || disk > 100)) {
                throw ProtocolException.validation("diskPercent must be between 0 and 100");
            }

            TelemetrySample sample = new TelemetrySample(
                    session.identity, collectedAt, receivedAt, cpu, memory, temperature, disk);
            long payloadBytes = payload.toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 90;
            processTelemetry(session, requestId, sample, payloadBytes);
        } catch (ProtocolException exception) {
            sendError(session, requestId, exception.code(), exception.getMessage(), true);
        } catch (IllegalArgumentException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        }
    }

    private void processTelemetry(
            ClientSession session, String requestId, TelemetrySample sample, long payloadBytes)
            throws IOException {
        DeviceState state = deviceStates.get(sample.deviceId());
        if (state == null) {
            if (session != null) {
                sendError(session, requestId, "VALIDATION_FAILED", "Unknown deviceId", true);
            }
            return;
        }
        List<JsonObject> alertUpdates = new ArrayList<>();
        JsonObject update;
        try {
            synchronized (state) {
                DeviceRules rules = state.rules();
                AlertEngine.CpuEvaluation cpuEvaluation =
                        alertEngine.evaluateCpu(
                                sample.cpuPercent(),
                                rules.cpuTrigger(),
                                rules.cpuRecover(),
                                rules.cpuRequiredSamples(),
                                state.cpuCounter(),
                                state.activeAlert(AlertRuleType.CPU) != null);
                AlertEngine.CpuEvaluation memoryEvaluation =
                        alertEngine.evaluateMemory(
                                sample.memoryPercent(),
                                rules.memoryTrigger(),
                                rules.memoryRecover(),
                                rules.memoryRequiredSamples(),
                                state.memoryCounter(),
                                state.activeAlert(AlertRuleType.MEMORY) != null);
                AlertAction temperatureAction = alertEngine.evaluateTemperature(
                        sample.temperatureCelsius(),
                        rules.temperatureTrigger(),
                        rules.temperatureRecover(),
                        state.activeAlert(AlertRuleType.TEMPERATURE) != null);
                List<AlertChange> alertChanges = new ArrayList<>();
                addAlertChange(
                        state,
                        AlertRuleType.CPU,
                        sample.cpuPercent(),
                        cpuEvaluation.action(),
                        sample.receivedAt(),
                        alertChanges);
                addAlertChange(
                        state,
                        AlertRuleType.MEMORY,
                        sample.memoryPercent(),
                        memoryEvaluation.action(),
                        sample.receivedAt(),
                        alertChanges);
                addAlertChange(
                        state,
                        AlertRuleType.TEMPERATURE,
                        sample.temperatureCelsius(),
                        temperatureAction,
                        sample.receivedAt(),
                        alertChanges);

                List<AlertRecord> persistedAlerts =
                        database.saveTelemetryAndAlertChanges(sample, alertChanges);
                state.cpuCounter(cpuEvaluation.counter());
                state.memoryCounter(memoryEvaluation.counter());
                state.applySample(sample);
                state.recordPayload(payloadBytes, sample.receivedAt());
                for (AlertRecord alert : persistedAlerts) {
                    if (alert.status() == AlertStatus.ACTIVE) {
                        state.putActiveAlert(alert);
                    } else {
                        state.removeActiveAlert(alert.ruleType());
                    }
                    LOGGER.info(() -> "Alert "
                            + alert.status()
                            + " device="
                            + alert.deviceId()
                            + " rule="
                            + alert.ruleType());
                    alertUpdates.add(alertUpdate(alert));
                }

                update = deviceUpdate(state);
            }
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Telemetry persistence failure for " + sample.deviceId(), exception);
            if (session != null) {
                sendError(session, requestId, "INTERNAL_ERROR", "Telemetry could not be stored", true);
            }
            return;
        }

        broadcastMonitor(update);
        for (JsonObject alertUpdate : alertUpdates) {
            broadcastMonitor(alertUpdate);
        }

        JsonObject ack = new JsonObject();
        ack.addProperty("receivedAt", sample.receivedAt());
        ack.addProperty("stored", true);
        if (session != null) {
            send(
                    session,
                    ProtocolMessage.response(
                            "TELEMETRY_ACK", requestId, sample.receivedAt(), ack));
        }
    }

    private static void addAlertChange(
            DeviceState state,
            AlertRuleType ruleType,
            Double value,
            AlertAction action,
            long now,
            List<AlertChange> alertChanges) {
        if (action == AlertAction.CREATE) {
            alertChanges.add(AlertChange.create(
                    ruleType, severityFor(ruleType, value, state.rules()), value, now));
        } else if (action == AlertAction.RESOLVE) {
            AlertRecord existing = state.activeAlert(ruleType);
            if (existing != null) {
                alertChanges.add(AlertChange.resolve(ruleType, existing.alertId(), now));
            }
        }
    }

    private void queryTelemetry(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            String deviceId = ProtocolValidator.requiredString(payload, "deviceId", 64);
            long from = ProtocolValidator.requiredLong(payload, "fromMillis");
            long to = ProtocolValidator.requiredLong(payload, "toMillis");
            long rawLimit = ProtocolValidator.requiredLong(payload, "limit");
            if (from > to || rawLimit < 1 || rawLimit > 1000) {
                throw ProtocolException.validation("Invalid telemetry query range or limit");
            }
            if (!deviceStates.containsKey(deviceId)) {
                throw ProtocolException.validation("Unknown deviceId");
            }
            List<TelemetrySample> samples =
                    database.queryTelemetry(deviceId, from, to, (int) rawLimit);
            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("deviceId", deviceId);
            JsonArray array = new JsonArray();
            for (TelemetrySample sample : samples) {
                array.add(telemetryJson(sample));
            }
            responsePayload.add("samples", array);
            send(
                    session,
                    ProtocolMessage.response(
                            "TELEMETRY_HISTORY",
                            requestId,
                            System.currentTimeMillis(),
                            responsePayload));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "QUERY_INVALID", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Telemetry query failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Telemetry query failed", true);
        }
    }

    private void queryAlerts(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            Optional<String> deviceId = optionalString(payload, "deviceId", 64);
            Optional<AlertRuleType> ruleType = optionalEnum(payload, "ruleType", AlertRuleType.class);
            Optional<AlertStatus> status = optionalEnum(payload, "status", AlertStatus.class);
            long from = ProtocolValidator.requiredLong(payload, "fromMillis");
            long to = ProtocolValidator.requiredLong(payload, "toMillis");
            long rawLimit = ProtocolValidator.requiredLong(payload, "limit");
            if (from > to || rawLimit < 1 || rawLimit > 500) {
                throw ProtocolException.validation("Invalid alert query range or limit");
            }
            List<AlertRecord> alerts =
                    database.queryAlerts(deviceId, ruleType, status, from, to, (int) rawLimit);
            JsonObject responsePayload = new JsonObject();
            JsonArray array = new JsonArray();
            for (AlertRecord alert : alerts) {
                array.add(alertJson(alert));
            }
            responsePayload.add("alerts", array);
            send(
                    session,
                    ProtocolMessage.response(
                            "ALERT_HISTORY",
                            requestId,
                            System.currentTimeMillis(),
                            responsePayload));
        } catch (ProtocolException | IllegalArgumentException exception) {
            sendError(session, requestId, "QUERY_INVALID", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Alert query failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Alert query failed", true);
        }
    }

    private void acknowledgeAlert(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            long alertId = ProtocolValidator.requiredLong(payload, "alertId");
            String comment = optionalString(payload, "comment", 200).orElse("");
            long now = System.currentTimeMillis();
            AlertRecord acknowledged =
                    database.acknowledgeAlert(alertId, now, session.identity, comment);
            if (acknowledged.status() != AlertStatus.ACTIVE) {
                throw ProtocolException.validation("Alert is no longer active");
            }
            audit(session.identity, "ACK_ALERT", acknowledged.deviceId(),
                    acknowledged.ruleType() + (comment.isBlank() ? "" : " 备注: " + comment));
            DeviceState state = deviceStates.get(acknowledged.deviceId());
            if (state != null) {
                synchronized (state) {
                    state.putActiveAlert(acknowledged);
                }
            }
            JsonObject response = alertJson(acknowledged);
            send(
                    session,
                    ProtocolMessage.response(
                            "ALERT_ACKED",
                            requestId,
                            now,
                            response));
            broadcastMonitor(alertUpdate(acknowledged));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "QUERY_INVALID", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Alert acknowledgement failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Alert acknowledgement failed", true);
        }
    }

    private void resolveAlertMessage(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            long alertId = ProtocolValidator.requiredLong(payload, "alertId");
            String comment = optionalString(payload, "comment", 200).orElse("");
            long now = System.currentTimeMillis();
            AlertRecord resolved = database.resolveAlert(alertId, now);
            if (resolved.status() != AlertStatus.RESOLVED || resolved.resolvedAt() == null) {
                throw ProtocolException.validation("Alert is no longer active");
            }
            audit(session.identity, "RESOLVE_ALERT", resolved.deviceId(),
                    resolved.ruleType() + (comment.isBlank() ? "" : " 备注: " + comment));
            DeviceState state = deviceStates.get(resolved.deviceId());
            if (state != null) {
                synchronized (state) {
                    state.removeActiveAlert(resolved.ruleType());
                }
            }
            send(session, ProtocolMessage.response("ALERT_RESOLVED", requestId, now, alertJson(resolved)));
            broadcastMonitor(alertUpdate(resolved));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "QUERY_INVALID", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Alert resolution failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Alert resolution failed", true);
        }
    }

    private void registerDeviceMessage(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            String deviceId = normalizeDeviceId(
                    ProtocolValidator.requiredString(payload, "deviceId", 64));
            String displayName = ProtocolValidator.requiredString(payload, "displayName", 64);
            String location = optionalString(payload, "location", 64).orElse("");
            DeviceRules rules = parseRules(payload);
            long now = System.currentTimeMillis();
            if (deviceStates.containsKey(deviceId)) {
                throw ProtocolException.validation("Device already exists: " + deviceId);
            }
            String credential = database.registerDevice(
                    deviceId, displayName, location, rules, now);
            DeviceInfo info = new DeviceInfo(deviceId, displayName, location, true, now, rules);
            DeviceState state = DeviceState.from(info);
            deviceStates.put(deviceId, state);
            audit(session.identity, "REGISTER_DEVICE", deviceId, displayName + " @ " + location);

            JsonObject response = deviceJson(state);
            response.addProperty("credential", credential);
            send(session, ProtocolMessage.response(
                    "DEVICE_REGISTERED", requestId, now, response));
            broadcastMonitor(deviceUpdate(state));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Device registration failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Device registration failed", true);
        }
    }

    private void updateDeviceMessage(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            String deviceId = ProtocolValidator.requiredString(payload, "deviceId", 64);
            String displayName = ProtocolValidator.requiredString(payload, "displayName", 64);
            String location = optionalString(payload, "location", 64).orElse("");
            DeviceState state = deviceStates.get(deviceId);
            if (state == null) {
                throw ProtocolException.validation("Unknown deviceId: " + deviceId);
            }
            database.updateDevice(deviceId, displayName, location);
            synchronized (state) {
                state.location(location);
            }
            audit(session.identity, "UPDATE_DEVICE", deviceId, displayName + " @ " + location);
            JsonObject response = deviceJson(state);
            send(session, ProtocolMessage.response(
                    "DEVICE_UPDATED", requestId, System.currentTimeMillis(), response));
            broadcastMonitor(deviceUpdate(state));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Device update failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Device update failed", true);
        }
    }

    private void setDeviceEnabledMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        try {
            String deviceId = ProtocolValidator.requiredString(payload, "deviceId", 64);
            boolean enabled = ProtocolValidator.requiredBoolean(payload, "enabled");
            DeviceState state = deviceStates.get(deviceId);
            if (state == null) {
                throw ProtocolException.validation("Unknown deviceId: " + deviceId);
            }
            database.setDeviceEnabled(deviceId, enabled);
            long now = System.currentTimeMillis();
            List<JsonObject> broadcasts = new ArrayList<>();
            synchronized (state) {
                state.enabled(enabled);
                if (!enabled) {
                    state.online(false);
                    List<AlertRecord> active = List.copyOf(state.activeAlerts().values());
                    for (AlertRecord alert : active) {
                        AlertRecord resolved = database.resolveAlert(alert.alertId(), now);
                        state.removeActiveAlert(alert.ruleType());
                        broadcasts.add(alertUpdate(resolved));
                    }
                    ClientSession deviceSession = deviceSessions.get(deviceId);
                    if (deviceSession != null) {
                        sendError(deviceSession, "device-disabled", "DEVICE_DISABLED",
                                "The device has been disabled by an operator", false);
                        deviceSession.close();
                        deviceSessions.remove(deviceId, deviceSession);
                    }
                }
            }
            audit(session.identity, enabled ? "ENABLE_DEVICE" : "DISABLE_DEVICE", deviceId,
                    enabled ? "enabled" : "disabled");
            JsonObject response = deviceJson(state);
            send(session, ProtocolMessage.response("DEVICE_UPDATED", requestId, now, response));
            broadcasts.add(deviceUpdate(state));
            for (JsonObject broadcast : broadcasts) {
                broadcastMonitor(broadcast);
            }
        } catch (ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Device enable change failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Device enable change failed", true);
        }
    }

    private void updateDeviceRulesMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        try {
            String deviceId = ProtocolValidator.requiredString(payload, "deviceId", 64);
            DeviceRules rules = parseRules(payload);
            DeviceState state = deviceStates.get(deviceId);
            if (state == null) {
                throw ProtocolException.validation("Unknown deviceId: " + deviceId);
            }
            long now = System.currentTimeMillis();
            database.updateDeviceRules(deviceId, rules, now);
            synchronized (state) {
                state.rules(rules);
            }
            audit(session.identity, "UPDATE_DEVICE_RULES", deviceId, "thresholds updated");
            JsonObject response = deviceJson(state);
            send(session, ProtocolMessage.response("DEVICE_UPDATED", requestId, now, response));
            broadcastMonitor(deviceUpdate(state));
        } catch (ProtocolException | IllegalArgumentException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Device rules update failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Device rules update failed", true);
        }
    }

    private void resetDeviceCredentialMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        try {
            String deviceId = ProtocolValidator.requiredString(payload, "deviceId", 64);
            if (!deviceStates.containsKey(deviceId)) {
                throw ProtocolException.validation("Unknown deviceId: " + deviceId);
            }
            String credential = database.issueNewDeviceCredential(deviceId);
            audit(session.identity, "RESET_DEVICE_CREDENTIAL", deviceId, "credential reissued");
            JsonObject response = new JsonObject();
            response.addProperty("deviceId", deviceId);
            response.addProperty("credential", credential);
            send(session, ProtocolMessage.response(
                    "DEVICE_CREDENTIAL_RESET", requestId, System.currentTimeMillis(), response));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Device credential reset failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Device credential reset failed", true);
        }
    }

    private void changePasswordMessage(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            String oldPassword = ProtocolValidator.requiredString(payload, "oldPassword", 256);
            String newPassword = ProtocolValidator.requiredString(payload, "newPassword", 256);
            if (newPassword.length() < 8 || newPassword.length() > 64) {
                throw ProtocolException.validation("New password must be 8 to 64 characters");
            }
            char[] newChars = newPassword.toCharArray();
            try {
                if (!database.authenticateOperator(session.identity, oldPassword.toCharArray())) {
                    throw ProtocolException.validation("Old password is incorrect");
                }
                database.changeOperatorPassword(session.identity, newChars);
            } finally {
                java.util.Arrays.fill(newChars, '\0');
            }
            audit(session.identity, "CHANGE_PASSWORD", session.identity, "password changed");
            JsonObject response = new JsonObject();
            response.addProperty("changed", true);
            send(session, ProtocolMessage.response(
                    "PASSWORD_CHANGED", requestId, System.currentTimeMillis(), response));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Password change failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Password change failed", true);
        }
    }

    private void queryAuditLogMessage(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            long rawLimit = ProtocolValidator.requiredLong(payload, "limit");
            if (rawLimit < 1 || rawLimit > 500) {
                throw ProtocolException.validation("Audit log limit must be between 1 and 500");
            }
            JsonObject response = new JsonObject();
            JsonArray array = new JsonArray();
            for (var event : database.queryAuditLog((int) rawLimit)) {
                JsonObject object = new JsonObject();
                object.addProperty("id", event.id());
                object.addProperty("actor", event.actor());
                object.addProperty("action", event.action());
                object.addProperty("target", event.target() == null ? "" : event.target());
                object.addProperty("detail", event.detail() == null ? "" : event.detail());
                object.addProperty("createdAt", event.createdAt());
                array.add(object);
            }
            response.add("events", array);
            send(session, ProtocolMessage.response(
                    "AUDIT_LOG", requestId, System.currentTimeMillis(), response));
        } catch (ProtocolException exception) {
            sendError(session, requestId, "QUERY_INVALID", exception.getMessage(), true);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Audit log query failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Audit log query failed", true);
        }
    }

    private void serverStatusMessage(ClientSession session, String requestId, JsonObject payload)
            throws IOException {
        try {
            long now = System.currentTimeMillis();
            JsonObject response = new JsonObject();
            response.addProperty("host", config.host());
            response.addProperty("port", port());
            response.addProperty("startedAt", startedAtMillis);
            response.addProperty("uptimeMillis", now - startedAtMillis);
            response.addProperty("deviceSessions", deviceSessions.size());
            response.addProperty("monitorSessions", monitorSessions.size());
            response.addProperty("registeredDevices", deviceStates.size());
            response.addProperty("telemetryCount", database.countTelemetry());
            response.addProperty("activeAlerts", database.countActiveAlerts());
            response.addProperty("databasePath", database.path().toString());
            long databaseSizeBytes = 0;
            try {
                databaseSizeBytes = Files.size(database.path());
            } catch (IOException exception) {
                LOGGER.log(Level.FINE, "Database file size unavailable", exception);
            }
            response.addProperty("databaseSizeBytes", databaseSizeBytes);
            response.addProperty("tlsEnabled", tlsEnabled());
            response.addProperty("tlsPort", tlsEnabled() ? tlsLocalPort() : 0);
            response.addProperty("tlsCertSubject", tlsCertSubject);
            response.addProperty("tlsHandshakeFailures", tlsHandshakeFailures());
            send(session, ProtocolMessage.response(
                    "SERVER_STATUS", requestId, now, response));
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Server status query failed", exception);
            sendError(session, requestId, "INTERNAL_ERROR", "Server status query failed", true);
        }
    }

    private DeviceRules parseRules(JsonObject payload) throws ProtocolException {
        JsonObject rules = payload.has("rules") && !payload.get("rules").isJsonNull()
                ? payload.getAsJsonObject("rules")
                : new JsonObject();
        double cpuTrigger = optionalDouble(rules, "cpuTrigger", config.cpuTriggerThreshold());
        double cpuRecover = optionalDouble(rules, "cpuRecover", config.cpuRecoverThreshold());
        int cpuSamples =
                (int) optionalLong(rules, "cpuSamples", config.cpuRequiredConsecutiveSamples());
        double memoryTrigger =
                optionalDouble(rules, "memoryTrigger", config.memoryTriggerThreshold());
        double memoryRecover =
                optionalDouble(rules, "memoryRecover", config.memoryRecoverThreshold());
        int memorySamples =
                (int) optionalLong(rules, "memorySamples", config.memoryRequiredConsecutiveSamples());
        double tempTrigger = optionalDouble(
                rules, "tempTrigger", config.temperatureTriggerThreshold());
        double tempRecover = optionalDouble(
                rules, "tempRecover", config.temperatureRecoverThreshold());
        try {
            return new DeviceRules(
                    cpuTrigger, cpuRecover, cpuSamples,
                    memoryTrigger, memoryRecover, memorySamples,
                    tempTrigger, tempRecover);
        } catch (IllegalArgumentException exception) {
            throw ProtocolException.validation(exception.getMessage());
        }
    }

    private static String normalizeDeviceId(String deviceId) {
        return deviceId.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void audit(String actor, String action, String target, String detail) {
        try {
            database.insertAuditEvent(
                    actor, action, target, detail, System.currentTimeMillis());
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Audit log write failed", exception);
        }
    }

    private static Optional<String> optionalString(JsonObject payload, String field, int maxLength)
            throws ProtocolException {
        JsonElement value = payload.get(field);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(ProtocolValidator.requiredString(payload, field, maxLength));
    }

    private static double optionalDouble(JsonObject payload, String field, double defaultValue) {
        JsonElement value = payload.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return defaultValue;
        }
        double parsed = value.getAsDouble();
        return Double.isFinite(parsed) ? parsed : defaultValue;
    }

    private static long optionalLong(JsonObject payload, String field, long defaultValue) {
        JsonElement value = payload.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return value.getAsLong();
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static <E extends Enum<E>> Optional<E> optionalEnum(
            JsonObject payload, String field, Class<E> enumType) throws ProtocolException {
        Optional<String> text = optionalString(payload, field, 32);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumType, text.get()));
        } catch (IllegalArgumentException exception) {
            throw ProtocolException.validation("Invalid value for " + field);
        }
    }

    private void registerServiceCheckMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        try {
            String name = ProtocolValidator.requiredString(payload, "name", 64);
            String host = ProtocolValidator.requiredString(payload, "host", 64);
            int port = (int) ProtocolValidator.requiredLong(payload, "port");
            int intervalMillis = (int) ProtocolValidator.requiredLong(payload, "intervalMillis");
            int failThreshold = (int) ProtocolValidator.requiredLong(payload, "failThreshold");
            ServiceCheck check = database.upsertServiceCheck(
                    name, host, port, intervalMillis, failThreshold, System.currentTimeMillis());
            ServiceCheckState state = new ServiceCheckState(check);
            serviceChecks.put(check.id(), state);
            JsonObject response = serviceCheckJson(state, System.currentTimeMillis());
            send(session, ProtocolMessage.response(
                    "SERVICE_CHECK_REGISTERED", requestId, System.currentTimeMillis(), response));
            broadcastMonitor(ProtocolMessage.response(
                    "SERVICE_CHECK_UPDATE",
                    "server-svc-update-" + UUID.randomUUID(),
                    System.currentTimeMillis(),
                    response));
        } catch (SQLException exception) {
            sendError(session, requestId, "INTERNAL_ERROR", "Service check could not be stored", true);
        } catch (IllegalArgumentException | ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        }
    }

    private void deleteServiceCheckMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        try {
            long checkId = ProtocolValidator.requiredLong(payload, "checkId");
            ServiceCheckState removed = serviceChecks.remove(checkId);
            database.deleteServiceCheck(checkId);
            JsonObject response = new JsonObject();
            response.addProperty("checkId", checkId);
            response.addProperty("deleted", removed != null);
            send(session, ProtocolMessage.response(
                    "SERVICE_CHECK_DELETED", requestId, System.currentTimeMillis(), response));
            broadcastMonitor(ProtocolMessage.response(
                    "SERVICE_CHECK_UPDATE",
                    "server-svc-update-" + UUID.randomUUID(),
                    System.currentTimeMillis(),
                    response));
        } catch (SQLException exception) {
            sendError(session, requestId, "INTERNAL_ERROR", "Service check could not be deleted", true);
        } catch (ProtocolException exception) {
            sendError(session, requestId, "VALIDATION_FAILED", exception.getMessage(), true);
        }
    }

    private void listServiceChecksMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        JsonArray checks = new JsonArray();
        long now = System.currentTimeMillis();
        for (ServiceCheckState state : serviceChecks.values()) {
            checks.add(serviceCheckJson(state, now));
        }
        JsonObject response = new JsonObject();
        response.add("checks", checks);
        send(session, ProtocolMessage.response(
                "SERVICE_CHECK_SNAPSHOT", requestId, now, response));
    }

    private void demoInjectMessage(
            ClientSession session, String requestId, JsonObject payload) throws IOException {
        long now = System.currentTimeMillis();
        try {
            String faultValue = ProtocolValidator.requiredString(payload, "fault", 32);
            if (!Set.of("CPU_SPIKE", "OFFLINE", "SERVICE_FAIL").contains(faultValue)) {
                throw ProtocolException.validation(
                        "fault must be CPU_SPIKE, OFFLINE or SERVICE_FAIL");
            }
            String deviceId = payload.has("deviceId") && !payload.get("deviceId").isJsonNull()
                    ? payload.get("deviceId").getAsString()
                    : null;
            Long checkId = payload.has("checkId") && !payload.get("checkId").isJsonNull()
                    ? payload.get("checkId").getAsLong()
                    : null;

            JsonObject response = new JsonObject();
            response.addProperty("fault", faultValue);
            switch (faultValue) {
                case "CPU_SPIKE" -> {
                    DeviceState target = resolveOnlineDevice(deviceId);
                    if (target == null) {
                        throw ProtocolException.validation("没有可注入的在线设备");
                    }
                    injectCpuSpike(target);
                    response.addProperty("deviceId", target.deviceId());
                }
                case "OFFLINE" -> {
                    DeviceState target = resolveOnlineDevice(deviceId);
                    if (target == null) {
                        throw ProtocolException.validation("没有可注入的在线设备");
                    }
                    long elapsed = Math.max(now - target.lastSeenAt(), 1);
                    markDeviceOffline(target, elapsed, now);
                    response.addProperty("deviceId", target.deviceId());
                }
                case "SERVICE_FAIL" -> {
                    ServiceCheckState target = resolveServiceCheck(checkId);
                    if (target == null) {
                        throw ProtocolException.validation("请先在系统设置中添加健康检查");
                    }
                    failServiceCheck(target, now);
                    response.addProperty("checkId", target.check().id());
                }
                default -> throw ProtocolException.validation(
                        "fault must be CPU_SPIKE, OFFLINE or SERVICE_FAIL");
            }
            audit(session.identity, "DEMO_INJECT", faultValue, "演示故障注入");
            send(session, ProtocolMessage.response("DEMO_INJECTED", requestId, now, response));
        } catch (ProtocolException exception) {
            sendError(session, requestId, exception.code(), exception.getMessage(), true);
        }
    }

    private DeviceState resolveOnlineDevice(String deviceId) {
        if (deviceId != null) {
            DeviceState state = deviceStates.get(deviceId);
            return state != null && state.enabled() && state.online() ? state : null;
        }
        return deviceStates.values().stream()
                .filter(DeviceState::enabled)
                .filter(DeviceState::online)
                .min(Comparator.comparing(DeviceState::deviceId))
                .orElse(null);
    }

    private void injectCpuSpike(DeviceState state) throws IOException {
        long now = System.currentTimeMillis();
        for (int index = 0; index < 3; index++) {
            TelemetrySample sample = new TelemetrySample(
                    state.deviceId(), now, now, 96 + index, 62, 55, 72.0);
            processTelemetry(null, "inject-" + UUID.randomUUID(), sample, 200);
        }
    }

    private void markDeviceOffline(DeviceState state, long elapsed, long now) {
        ClientSession session = deviceSessions.get(state.deviceId());
        synchronized (state) {
            if (!state.online()) {
                return;
            }
            state.online(false);
            try {
                AlertRecord alert = database.createAlert(
                        state.deviceId(), AlertRuleType.OFFLINE, elapsed / 1000.0, now);
                state.putActiveAlert(alert);
                LOGGER.info(() -> "Alert ACTIVE device="
                        + state.deviceId()
                        + " rule=OFFLINE elapsedSeconds="
                        + (elapsed / 1000.0));
                broadcastAlert(alert);
            } catch (SQLException exception) {
                LOGGER.log(Level.SEVERE, "Failed to create offline alert", exception);
            }
            broadcastMonitor(deviceUpdate(state));
        }
        if (session != null) {
            session.close();
            deviceSessions.remove(state.deviceId(), session);
        }
    }

    private ServiceCheckState resolveServiceCheck(Long checkId) {
        if (checkId != null) {
            return serviceChecks.get(checkId);
        }
        return serviceChecks.values().stream()
                .min(Comparator.comparingLong(state -> state.check().id()))
                .orElse(null);
    }

    private void failServiceCheck(ServiceCheckState state, long now) {
        AlertRecord alert;
        synchronized (state) {
            if (state.failing()) {
                return;
            }
            state.apply(false, true);
            state.lastCheckedAt(now);
            state.lastDurationMillis(0);
            try {
                database.recordServiceCheckResult(state.check().id(), now, false, 0);
                state.availabilityPercent(
                        database.serviceCheckAvailability(state.check().id(), 86_400_000L) * 100);
                alert = database.createAlert(
                        state.check().name(), AlertRuleType.SERVICE_DOWN, null, now);
            } catch (SQLException exception) {
                LOGGER.log(Level.SEVERE, "Failed to inject service failure", exception);
                alert = null;
            }
        }
        if (alert != null) {
            LOGGER.info(() -> "Alert ACTIVE service="
                    + state.check().name() + " rule=SERVICE_DOWN (demo inject)");
            broadcastAlert(alert);
        }
        broadcastMonitor(ProtocolMessage.response(
                "SERVICE_CHECK_UPDATE",
                "server-svc-inject-" + UUID.randomUUID(),
                now,
                serviceCheckJson(state, now)));
    }

    private void healthCheckPass(long now) {
        for (ServiceCheckState state : serviceChecks.values()) {
            ServiceCheck check = state.check();
            if (now - state.lastCheckedAt() < check.intervalMillis()) {
                continue;
            }
            boolean success;
            long durationMillis;
            HealthCheckTracker.Action action;
            synchronized (state) {
                long started = System.nanoTime();
                success = tcpProbe(check.host(), check.port());
                durationMillis = (System.nanoTime() - started) / 1_000_000L;
                action = HealthCheckTracker.evaluate(
                        state.consecutiveFailures(), state.failing(), success, check.failThreshold());
                state.apply(success, action == HealthCheckTracker.Action.FAIL);
                state.lastCheckedAt(now);
                state.lastDurationMillis(durationMillis);
            }
            try {
                database.recordServiceCheckResult(check.id(), now, success, durationMillis);
                state.availabilityPercent(
                        database.serviceCheckAvailability(check.id(), 86_400_000L) * 100);
            } catch (SQLException exception) {
                LOGGER.log(Level.SEVERE, "Failed to persist service check result", exception);
            }
            try {
                if (action == HealthCheckTracker.Action.FAIL) {
                    AlertRecord alert = database.createAlert(
                            check.name(), AlertRuleType.SERVICE_DOWN, null, now);
                    LOGGER.info(() -> "Alert ACTIVE service=" + check.name() + " rule=SERVICE_DOWN");
                    broadcastAlert(alert);
                } else if (action == HealthCheckTracker.Action.RECOVER) {
                    AlertRecord resolved =
                            database.resolveActiveAlert(check.name(), AlertRuleType.SERVICE_DOWN, now);
                    if (resolved != null) {
                        LOGGER.info(() -> "Alert RESOLVED service="
                                + check.name() + " rule=SERVICE_DOWN");
                        broadcastAlert(resolved);
                    }
                }
            } catch (SQLException exception) {
                LOGGER.log(Level.SEVERE, "Failed to update service alert", exception);
            }
            broadcastMonitor(ProtocolMessage.response(
                    "SERVICE_CHECK_UPDATE",
                    "server-svc-update-" + UUID.randomUUID(),
                    now,
                    serviceCheckJson(state, now)));
        }
    }

    private static boolean tcpProbe(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2_000);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static JsonObject serviceCheckJson(ServiceCheckState state, long now) {
        ServiceCheck check = state.check();
        JsonObject object = new JsonObject();
        object.addProperty("checkId", check.id());
        object.addProperty("name", check.name());
        object.addProperty("host", check.host());
        object.addProperty("port", check.port());
        object.addProperty("intervalMillis", check.intervalMillis());
        object.addProperty("failThreshold", check.failThreshold());
        object.addProperty("enabled", check.enabled());
        object.addProperty("online", !state.failing());
        object.addProperty("consecutiveFailures", state.consecutiveFailures());
        object.addProperty("lastCheckedAt", state.lastCheckedAt());
        object.addProperty("lastDurationMillis", state.lastDurationMillis());
        object.addProperty("availabilityPercent",
                Math.round(state.availabilityPercent() * 10.0) / 10.0);
        return object;
    }

    private static final class ServiceCheckState {
        private final ServiceCheck check;
        private int consecutiveFailures;
        private boolean failing;
        private long lastCheckedAt;
        private long lastDurationMillis;
        private double availabilityPercent = 100;

        private ServiceCheckState(ServiceCheck check) {
            this.check = check;
        }

        ServiceCheck check() {
            return check;
        }

        int consecutiveFailures() {
            return consecutiveFailures;
        }

        boolean failing() {
            return failing;
        }

        long lastCheckedAt() {
            return lastCheckedAt;
        }

        void lastCheckedAt(long value) {
            lastCheckedAt = value;
        }

        long lastDurationMillis() {
            return lastDurationMillis;
        }

        void lastDurationMillis(long value) {
            lastDurationMillis = value;
        }

        double availabilityPercent() {
            return availabilityPercent;
        }

        void availabilityPercent(double value) {
            availabilityPercent = value;
        }

        void apply(boolean success, boolean startFailing) {
            consecutiveFailures = success ? 0 : consecutiveFailures + 1;
            failing = startFailing || (failing && !success);
        }
    }

    private void runMaintenance() {
        if (!running.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastRetentionRunAt == 0 || now - lastRetentionRunAt >= 3_600_000L) {
            lastRetentionRunAt = now;
            try {
                long cutoffMillis =
                        now - config.telemetryRetentionDays() * 86_400_000L;
                long deleted = database.deleteTelemetryOlderThan(cutoffMillis);
                if (deleted > 0) {
                    LOGGER.info(() -> "Retention cleanup removed "
                            + deleted + " telemetry samples older than " + cutoffMillis);
                }
            } catch (SQLException exception) {
                LOGGER.log(Level.SEVERE, "Retention cleanup failed", exception);
            }
        }
        healthCheckPass(now);
        for (DeviceState state : deviceStates.values()) {
            if (!state.enabled()) {
                continue;
            }
            boolean expired;
            long elapsed;
            synchronized (state) {
                elapsed = now - state.lastSeenAt();
                expired = state.online() && elapsed >= config.offlineTimeoutMillis();
            }
            if (expired) {
                markDeviceOffline(state, elapsed, now);
            }
        }

        for (ClientSession monitor : monitorSessions) {
            if (now - monitor.lastActivityAt >= MONITOR_INACTIVITY_TIMEOUT_MILLIS) {
                monitorSessions.remove(monitor);
                monitor.close();
            }
        }
    }

    private void sendDeviceSnapshot(ClientSession session) throws IOException {
        JsonObject payload = new JsonObject();
        JsonArray array = new JsonArray();
        for (DeviceState state : deviceStates.values()) {
            synchronized (state) {
                array.add(deviceJson(state));
            }
        }
        payload.add("devices", array);
        send(
                session,
                ProtocolMessage.response(
                        "DEVICE_SNAPSHOT",
                        "server-device-snapshot-" + UUID.randomUUID(),
                        System.currentTimeMillis(),
                        payload));
    }

    private void sendAlertSnapshot(ClientSession session) throws IOException {
        JsonObject payload = new JsonObject();
        JsonArray array = new JsonArray();
        for (DeviceState state : deviceStates.values()) {
            synchronized (state) {
                for (AlertRecord alert : state.activeAlerts().values()) {
                    array.add(alertJson(alert));
                }
            }
        }
        payload.add("alerts", array);
        send(
                session,
                ProtocolMessage.response(
                        "ALERT_SNAPSHOT",
                        "server-alert-snapshot-" + UUID.randomUUID(),
                        System.currentTimeMillis(),
                        payload));
    }

    private void broadcastMonitor(JsonObject message) {
        for (ClientSession monitor : monitorSessions) {
            if (!trySend(monitor, message)) {
                monitorSessions.remove(monitor);
                monitor.close();
            }
        }
    }

    private void broadcastAlert(AlertRecord alert) {
        broadcastMonitor(alertUpdate(alert));
    }

    private static JsonObject deviceUpdate(DeviceState state) {
        JsonObject payload = deviceJson(state);
        return ProtocolMessage.response(
                "DEVICE_UPDATE",
                "server-device-update-" + UUID.randomUUID(),
                System.currentTimeMillis(),
                payload);
    }

    private static JsonObject deviceJson(DeviceState state) {
        JsonObject object = new JsonObject();
        object.addProperty("deviceId", state.deviceId());
        object.addProperty("displayName", state.displayName());
        object.addProperty("location", state.location());
        object.addProperty("enabled", state.enabled());
        object.addProperty("createdAt", state.createdAt());
        object.addProperty("online", state.online());
        object.addProperty("real", state.real());
        object.addProperty("lastSeenAt", state.lastSeenAt());
        addNullableNumber(object, "cpuPercent", state.cpuPercent());
        addNullableNumber(object, "memoryPercent", state.memoryPercent());
        addNullableNumber(object, "temperatureCelsius", state.temperatureCelsius());
        addNullableNumber(object, "diskPercent", state.diskPercent());
        object.addProperty("latencyMillis", state.latencyMillis());
        object.addProperty("networkKbps", Math.round(state.networkKbps() * 10.0) / 10.0);
        JsonObject rules = new JsonObject();
        DeviceRules value = state.rules();
        rules.addProperty("cpuTrigger", value.cpuTrigger());
        rules.addProperty("cpuRecover", value.cpuRecover());
        rules.addProperty("cpuSamples", value.cpuRequiredSamples());
        rules.addProperty("memoryTrigger", value.memoryTrigger());
        rules.addProperty("memoryRecover", value.memoryRecover());
        rules.addProperty("memorySamples", value.memoryRequiredSamples());
        rules.addProperty("tempTrigger", value.temperatureTrigger());
        rules.addProperty("tempRecover", value.temperatureRecover());
        object.add("rules", rules);
        return object;
    }

    private static JsonObject telemetryJson(TelemetrySample sample) {
        JsonObject object = new JsonObject();
        object.addProperty("deviceId", sample.deviceId());
        object.addProperty("collectedAt", sample.collectedAt());
        object.addProperty("receivedAt", sample.receivedAt());
        object.addProperty("cpuPercent", sample.cpuPercent());
        object.addProperty("memoryPercent", sample.memoryPercent());
        object.addProperty("temperatureCelsius", sample.temperatureCelsius());
        return object;
    }

    private static JsonObject alertUpdate(AlertRecord alert) {
        return ProtocolMessage.response(
                "ALERT_UPDATE",
                "server-alert-update-" + UUID.randomUUID(),
                System.currentTimeMillis(),
                alertJson(alert));
    }

    private static JsonObject alertJson(AlertRecord alert) {
        JsonObject object = new JsonObject();
        object.addProperty("alertId", alert.alertId());
        object.addProperty("deviceId", alert.deviceId());
        object.addProperty("ruleType", alert.ruleType().name());
        object.addProperty("severity", alert.severity().name());
        object.addProperty("status", alert.status().name());
        addNullableNumber(object, "triggerValue", alert.triggerValue());
        object.addProperty("triggeredAt", alert.triggeredAt());
        if (alert.resolvedAt() == null) {
            object.add("resolvedAt", com.google.gson.JsonNull.INSTANCE);
        } else {
            object.addProperty("resolvedAt", alert.resolvedAt());
        }
        if (alert.acknowledgedAt() == null) {
            object.add("acknowledgedAt", com.google.gson.JsonNull.INSTANCE);
        } else {
            object.addProperty("acknowledgedAt", alert.acknowledgedAt());
        }
        if (alert.acknowledgedBy() == null) {
            object.add("acknowledgedBy", com.google.gson.JsonNull.INSTANCE);
        } else {
            object.addProperty("acknowledgedBy", alert.acknowledgedBy());
        }
        if (alert.ackComment() == null) {
            object.add("ackComment", com.google.gson.JsonNull.INSTANCE);
        } else {
            object.addProperty("ackComment", alert.ackComment());
        }
        return object;
    }

    private static void addNullableNumber(JsonObject object, String name, Number value) {
        if (value == null) {
            object.add(name, com.google.gson.JsonNull.INSTANCE);
        } else {
            object.addProperty(name, value);
        }
    }

    private void send(ClientSession session, JsonObject message) throws IOException {
        if (session.channel != null && !session.closed.get()) {
            session.channel.write(message);
        }
    }

    private boolean trySend(ClientSession session, JsonObject message) {
        try {
            send(session, message);
            return true;
        } catch (IOException exception) {
            LOGGER.log(Level.FINE, "Monitor write failed: " + session.remoteAddress(), exception);
            return false;
        }
    }

    private void sendError(
            ClientSession session,
            String requestId,
            String code,
            String detail,
            boolean recoverable) {
        try {
            send(
                    session,
                    ProtocolMessage.error(
                            requestId,
                            System.currentTimeMillis(),
                            code,
                            detail == null ? "Protocol error" : detail,
                            recoverable));
        } catch (IOException exception) {
            LOGGER.log(Level.FINE, "Error response write failed", exception);
        }
    }

    private void unregister(ClientSession session) {
        if (session.role == Role.DEVICE && session.identity != null) {
            deviceSessions.remove(session.identity, session);
        } else if (session.role == Role.MONITOR) {
            monitorSessions.remove(session);
        }
    }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        sendShutdownNotice();
        closeQuietly(serverSocket);
        closeTlsQuietly(tlsServerSocket);
        for (ClientSession session : new ArrayList<>(deviceSessions.values())) {
            session.close();
        }
        for (ClientSession session : monitorSessions) {
            session.close();
        }
        deviceSessions.clear();
        monitorSessions.clear();
        connectionPool.shutdownNow();
        maintenanceExecutor.shutdownNow();
        try {
            connectionPool.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            maintenanceExecutor.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        stopped.countDown();
        LOGGER.info("Device monitor server stopped");
    }

    private void sendShutdownNotice() {
        JsonObject error = ProtocolMessage.error(
                "server-shutdown",
                System.currentTimeMillis(),
                "SERVER_SHUTTING_DOWN",
                "Server is shutting down",
                false);
        for (ClientSession session : deviceSessions.values()) {
            trySend(session, error);
        }
        for (ClientSession session : monitorSessions) {
            trySend(session, error);
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing else to do during shutdown.
            }
        }
    }

    private static void closeTlsQuietly(javax.net.ssl.SSLServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing else to do during shutdown.
            }
        }
    }

    private static ThreadFactory namedThreads(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class ClientSession {
        private final Socket socket;
        private final String remoteAddress;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile JsonLineChannel channel;
        private volatile boolean authenticated;
        private volatile Role role;
        private volatile String identity;
        private volatile String sessionId;
        private volatile long lastActivityAt;
        private int authAttempts;

        private ClientSession(Socket socket) {
            this.socket = socket;
            this.remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
            this.lastActivityAt = System.currentTimeMillis();
        }

        private void touch() {
            lastActivityAt = System.currentTimeMillis();
        }

        private String remoteAddress() {
            return remoteAddress;
        }

        private void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Closing an already failed socket is harmless.
                }
            }
        }
    }
}

