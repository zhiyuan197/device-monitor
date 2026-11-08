package com.shenliwei.devicewatch.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.shenliwei.devicewatch.monitor.AlertView;
import com.shenliwei.devicewatch.monitor.DeviceView;
import com.shenliwei.devicewatch.monitor.MonitorListener;
import com.shenliwei.devicewatch.monitor.ServiceCheckView;
import java.awt.Desktop;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Facade that mirrors monitor events into a per-second JSON snapshot
 * broadcast to browser sessions over the loopback WebSocket server.
 */
public final class WebDashboard implements MonitorListener, Closeable {

    static final int TREND_LIMIT = 120;
    static final int RECENT_ALERTS_LIMIT = 50;

    private final WebDashboardConfig config;
    private final String serverHost;
    private final int serverPort;
    private final Clock clock;
    private final long startedAt;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private final Map<String, DeviceView> devices = new ConcurrentHashMap<>();
    private final Map<Long, ServiceCheckView> serviceChecks = new ConcurrentHashMap<>();
    private final Deque<AlertView> recentAlerts = new ArrayDeque<>();
    private final Map<String, TrendBuffer> trends = new ConcurrentHashMap<>();

    private volatile boolean connected;
    private volatile long sequence;
    private volatile WebServer server;
    private volatile ScheduledExecutorService ticker;

    public WebDashboard(
            WebDashboardConfig config, String serverHost, int serverPort) {
        this(config, serverHost, serverPort, Clock.systemUTC());
    }

    WebDashboard(
            WebDashboardConfig config, String serverHost, int serverPort, Clock clock) {
        this.config = config;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clock = clock;
        this.startedAt = clock.millis();
    }

    public String url() {
        WebServer current = server;
        return current == null
                ? config.url()
                : "http://" + config.bindHost() + ":" + current.port() + "/";
    }

    public synchronized boolean start() throws IOException {
        if (!config.enabled() || server != null) {
            return server != null;
        }
        WebServer newServer = new WebServer(
                config.bindHost(), config.port(), this::onSessionOpened);
        newServer.start();
        server = newServer;
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "web-dashboard-ticker");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        ticker = executor;
        return true;
    }

    void onSessionOpened(WebSocketSession session) {
        sampleTrends();
        sequence++;
        session.sendText(buildSnapshotJson());
    }

    public boolean isRunning() {
        WebServer current = server;
        return current != null && current.isRunning();
    }

    public int webPort() {
        WebServer current = server;
        return current == null ? config.port() : current.port();
    }

    public int sessionCount() {
        WebServer current = server;
        return current == null ? 0 : current.sessions().size();
    }

    public boolean openBrowser() {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url()));
                return true;
            }
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    public String buildSnapshotJson() {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("type", "snapshot");
        snapshot.addProperty("seq", sequence);
        snapshot.addProperty("ts", clock.millis());
        snapshot.addProperty("connected", connected);
        JsonObject serverObject = new JsonObject();
        serverObject.addProperty("host", serverHost);
        serverObject.addProperty("port", serverPort);
        snapshot.add("server", serverObject);
        snapshot.addProperty("startedAt", startedAt);
        snapshot.add("stats", statsJson());
        snapshot.add("devices", devicesJson());
        snapshot.add("alerts", alertsJson());
        snapshot.add("checks", checksJson());
        snapshot.add("trends", trendsJson());
        return gson.toJson(snapshot);
    }

    private JsonObject statsJson() {
        JsonObject stats = new JsonObject();
        long online = devices.values().stream().filter(DeviceView::online).count();
        long real = devices.values().stream().filter(DeviceView::real).count();
        long activeAlerts = recentAlerts.stream()
                .filter(alert -> "ACTIVE".equals(alert.status()))
                .count();
        double latencyTotal = 0;
        int latencyCount = 0;
        for (DeviceView device : devices.values()) {
            if (device.online() && device.latencyMillis() > 0) {
                latencyTotal += device.latencyMillis();
                latencyCount++;
            }
        }
        stats.addProperty("online", online);
        stats.addProperty("total", devices.size());
        stats.addProperty("real", real);
        stats.addProperty("activeAlerts", activeAlerts);
        stats.addProperty("avgLatencyMs", latencyCount == 0
                ? 0 : Math.round(latencyTotal / latencyCount));
        return stats;
    }

    private JsonArray devicesJson() {
        JsonArray array = new JsonArray();
        devices.values().stream()
                .sorted(Comparator.comparing(DeviceView::deviceId))
                .forEach(device -> array.add(deviceJson(device)));
        return array;
    }

    private static JsonObject deviceJson(DeviceView device) {
        JsonObject object = new JsonObject();
        object.addProperty("id", device.deviceId());
        object.addProperty("name", device.displayName());
        object.addProperty("location", device.location());
        object.addProperty("online", device.online());
        object.addProperty("real", device.real());
        object.addProperty("enabled", device.enabled());
        object.addProperty("cpu", device.cpuPercent());
        object.addProperty("memory", device.memoryPercent());
        object.addProperty("temperature", device.temperatureCelsius());
        object.addProperty("disk", device.diskPercent());
        object.addProperty("latencyMs", device.latencyMillis());
        object.addProperty("networkKbps", device.networkKbps());
        object.addProperty("lastSeenAt", device.lastSeenAt());
        return object;
    }

    private JsonArray alertsJson() {
        JsonArray array = new JsonArray();
        synchronized (recentAlerts) {
            for (AlertView alert : recentAlerts) {
                JsonObject object = new JsonObject();
                object.addProperty("id", alert.alertId());
                object.addProperty("deviceId", alert.deviceId());
                object.addProperty("ruleType", alert.ruleType());
                object.addProperty("severity", alert.severity());
                object.addProperty("status", alert.status());
                object.addProperty("triggerValue", alert.triggerValue());
                object.addProperty("triggeredAt", alert.triggeredAt());
                array.add(object);
            }
        }
        return array;
    }

    private JsonArray checksJson() {
        JsonArray array = new JsonArray();
        serviceChecks.values().stream()
                .sorted(Comparator.comparingLong(ServiceCheckView::checkId))
                .limit(8)
                .forEach(check -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("id", check.checkId());
                    object.addProperty("name", check.name());
                    object.addProperty("online", check.online());
                    object.addProperty("availabilityPercent", check.availabilityPercent());
                    array.add(object);
                });
        return array;
    }

    private JsonObject trendsJson() {
        JsonObject root = new JsonObject();
        trends.forEach((deviceId, buffer) -> {
            JsonObject series = new JsonObject();
            series.add("ts", numbers(buffer, TrendPoint::ts));
            series.add("cpu", numbers(buffer, TrendPoint::cpu));
            series.add("memory", numbers(buffer, TrendPoint::memory));
            series.add("networkKbps", numbers(buffer, TrendPoint::networkKbps));
            series.add("latencyMs", numbers(buffer, TrendPoint::latencyMs));
            root.add(deviceId, series);
        });
        return root;
    }

    private interface ValueExtractor {
        double extract(TrendPoint point);
    }

    private static JsonArray numbers(TrendBuffer buffer, ValueExtractor extractor) {
        JsonArray array = new JsonArray();
        synchronized (buffer) {
            for (TrendPoint point : buffer.points()) {
                double value = extractor.extract(point);
                if (Double.isNaN(value)) {
                    array.add(JsonNull.INSTANCE);
                } else {
                    array.add(value);
                }
            }
        }
        return array;
    }

    void sampleTrends() {
        long now = clock.millis();
        for (DeviceView device : devices.values()) {
            TrendBuffer buffer = trends.computeIfAbsent(
                    device.deviceId(), ignored -> new TrendBuffer());
            buffer.add(new TrendPoint(
                    now,
                    valueOrNaN(device.cpuPercent()),
                    valueOrNaN(device.memoryPercent()),
                    device.networkKbps(),
                    device.online() ? device.latencyMillis() : Double.NaN));
        }
    }

    private static double valueOrNaN(Double value) {
        return value == null ? Double.NaN : value;
    }

    private void tick() {
        WebServer current = server;
        if (current == null || current.sessions().isEmpty()) {
            return;
        }
        sampleTrends();
        sequence++;
        current.broadcastText(buildSnapshotJson());
    }

    private void pushEvent(String type, JsonObject payload) {
        WebServer current = server;
        if (current == null || current.sessions().isEmpty()) {
            return;
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", type);
        envelope.addProperty("ts", clock.millis());
        envelope.add(type, payload);
        current.broadcastText(gson.toJson(envelope));
    }

    @Override
    public void onDeviceSnapshot(List<DeviceView> snapshot) {
        devices.clear();
        for (DeviceView device : snapshot) {
            devices.put(device.deviceId(), device);
            trends.computeIfAbsent(device.deviceId(), ignored -> new TrendBuffer());
        }
        sampleTrends();
    }

    @Override
    public void onDeviceUpdate(DeviceView device) {
        devices.put(device.deviceId(), device);
        trends.computeIfAbsent(device.deviceId(), ignored -> new TrendBuffer());
        pushEvent("device", deviceJson(device));
    }

    @Override
    public void onAlertSnapshot(List<AlertView> alerts) {
        synchronized (recentAlerts) {
            recentAlerts.clear();
            alerts.stream()
                    .sorted(Comparator.comparingLong(AlertView::triggeredAt).reversed())
                    .limit(RECENT_ALERTS_LIMIT)
                    .forEach(recentAlerts::addLast);
        }
    }

    @Override
    public void onAlertUpdate(AlertView alert) {
        synchronized (recentAlerts) {
            recentAlerts.removeIf(item -> item.alertId() == alert.alertId());
            recentAlerts.addFirst(alert);
            while (recentAlerts.size() > RECENT_ALERTS_LIMIT) {
                recentAlerts.removeLast();
            }
        }
        if ("ACTIVE".equals(alert.status())) {
            pushEvent("alert", alertJson(alert));
        }
    }

    private static JsonObject alertJson(AlertView alert) {
        JsonObject object = new JsonObject();
        object.addProperty("id", alert.alertId());
        object.addProperty("deviceId", alert.deviceId());
        object.addProperty("ruleType", alert.ruleType());
        object.addProperty("severity", alert.severity());
        object.addProperty("status", alert.status());
        object.addProperty("triggerValue", alert.triggerValue());
        object.addProperty("triggeredAt", alert.triggeredAt());
        return object;
    }

    @Override
    public void onConnectionState(boolean connectedState, String detail) {
        connected = connectedState;
        JsonObject payload = new JsonObject();
        payload.addProperty("connected", connectedState);
        payload.addProperty("detail", detail);
        pushEvent("status", payload);
    }

    @Override
    public void onServiceCheckSnapshot(List<ServiceCheckView> checks) {
        serviceChecks.clear();
        for (ServiceCheckView check : checks) {
            serviceChecks.put(check.checkId(), check);
        }
    }

    @Override
    public void onServiceCheckUpdate(ServiceCheckView check) {
        serviceChecks.put(check.checkId(), check);
    }

    @Override
    public void onServiceCheckDeleted(long checkId) {
        serviceChecks.remove(checkId);
    }

    public synchronized void stop() {
        ScheduledExecutorService executor = ticker;
        ticker = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        WebServer current = server;
        server = null;
        if (current != null) {
            current.close();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private static final class TrendBuffer {
        private final Deque<TrendPoint> points = new ArrayDeque<>();

        void add(TrendPoint point) {
            points.addLast(point);
            while (points.size() > TREND_LIMIT) {
                points.removeFirst();
            }
        }

        List<TrendPoint> points() {
            return new ArrayList<>(points);
        }
    }

    private record TrendPoint(
            long ts, double cpu, double memory, double networkKbps, double latencyMs) {
    }
}
