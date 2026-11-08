package com.shenliwei.devicewatch.loadtest;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.monitor.DeviceView;
import com.shenliwei.devicewatch.monitor.MonitorClient;
import com.shenliwei.devicewatch.monitor.MonitorListener;
import com.shenliwei.devicewatch.persistence.Database;
import com.shenliwei.devicewatch.persistence.DemoCredentials;
import com.shenliwei.devicewatch.server.MonitorServer;
import com.shenliwei.devicewatch.simulator.DeviceClient;
import com.shenliwei.devicewatch.simulator.SimulatorConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LoadTestApp {
    private LoadTestApp() {
    }

    public static void run(String[] args) throws Exception {
        int deviceCount = 20;
        long durationSeconds = 600;
        long intervalMillis = 1_000;
        Path outputDirectory = Path.of("test-results");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--devices" ->
                        deviceCount = Integer.parseInt(requireValue(args, ++i, "--devices"));
                case "--duration-seconds" ->
                        durationSeconds = Long.parseLong(requireValue(args, ++i, "--duration-seconds"));
                case "--interval-millis" ->
                        intervalMillis = Long.parseLong(requireValue(args, ++i, "--interval-millis"));
                case "--output-dir" ->
                        outputDirectory = Path.of(requireValue(args, ++i, "--output-dir"));
                case "--help", "-h" -> {
                    System.out.println(
                            "load-test [--devices 20] [--duration-seconds 600] "
                                    + "[--interval-millis 1000] [--output-dir test-results]");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown load-test option: " + args[i]);
            }
        }
        if (deviceCount < 1 || deviceCount > 100 || durationSeconds < 1 || intervalMillis < 100) {
            throw new IllegalArgumentException("Invalid device count, duration, or interval");
        }

        Path workDirectory = Files.createTempDirectory("device-monitor-load-");
        Path databasePath = workDirectory.resolve("load-test.db");
        Path credentialsPath = workDirectory.resolve("load-credentials.properties");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                deviceCount,
                "load-monitor",
                "load-monitor-password".toCharArray(),
                credentialsPath,
                "127.0.0.1",
                5050);

        AppConfig config = AppConfig.defaults()
                .withPort(0)
                .withDatabasePath(databasePath);
        config = new AppConfig(
                "127.0.0.1",
                0,
                config.authTimeoutMillis(),
                config.maxMessageBytes(),
                config.heartbeatIntervalMillis(),
                config.offlineTimeoutMillis(),
                config.cpuTriggerThreshold(),
                config.cpuRecoverThreshold(),
                config.cpuRequiredConsecutiveSamples(),
                config.temperatureTriggerThreshold(),
                config.temperatureRecoverThreshold(),
                config.memoryTriggerThreshold(),
                config.memoryRecoverThreshold(),
                config.memoryRequiredConsecutiveSamples(),
                databasePath);

        List<Long> updateLatenciesNanos = Collections.synchronizedList(new ArrayList<>());
        Map<String, Long> lastSendNanos = new ConcurrentHashMap<>();
        AtomicLong updateCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        CountDownLatch snapshotReady = new CountDownLatch(1);

        MonitorServer server = new MonitorServer(config, database);
        MonitorClient monitor = null;
        ExecutorService executor = Executors.newFixedThreadPool(deviceCount);
        CountDownLatch clientsReady = new CountDownLatch(deviceCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        Path summaryFile = outputDirectory.resolve("load-test-summary.csv");
        Path samplesFile = outputDirectory.resolve("load-test-latency-samples.csv");

        try {
            server.start();
            int port = server.port();
            monitor = new MonitorClient(
                    "127.0.0.1",
                    port,
                    credentials.operatorUsername(),
                    credentials.operatorPassword().toCharArray(),
                    new MonitorListener() {
                        @Override
                        public void onDeviceSnapshot(List<DeviceView> devices) {
                            snapshotReady.countDown();
                        }

                        @Override
                        public void onDeviceUpdate(DeviceView device) {
                            Long sentAt = lastSendNanos.remove(device.deviceId());
                            if (sentAt != null) {
                                updateLatenciesNanos.add(System.nanoTime() - sentAt);
                                updateCount.incrementAndGet();
                            }
                        }
                    });
            monitor.connect();
            if (!snapshotReady.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Monitor snapshot was not received");
            }

            SimulatorConfig simulatorConfig = new SimulatorConfig(
                    "127.0.0.1",
                    port,
                    credentials.operatorUsername(),
                    credentials.operatorPassword(),
                    credentials.devices().stream()
                            .map(device -> new SimulatorConfig.DeviceAccount(
                                    device.deviceId(), device.credential()))
                            .toList());
            final long testDurationSeconds = durationSeconds;
            final long sampleIntervalMillis = intervalMillis;

            for (int i = 0; i < deviceCount; i++) {
                SimulatorConfig.DeviceAccount account = simulatorConfig.devices().get(i);
                tasks.add(executor.submit(() -> {
                    DeviceClient device = null;
                    try {
                        device = DeviceClient.connect(simulatorConfig, account);
                        clientsReady.countDown();
                        start.await();
                        long deadline = System.nanoTime()
                                + Duration.ofSeconds(testDurationSeconds).toNanos();
                        long sequence = 0;
                        long nextHeartbeat = System.nanoTime();
                        while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                            if (System.nanoTime() >= nextHeartbeat) {
                                device.sendHeartbeat(sequence++);
                                nextHeartbeat = System.nanoTime()
                                        + TimeUnit.SECONDS.toNanos(5);
                            }
                            long sentAt = System.nanoTime();
                            lastSendNanos.put(account.deviceId(), sentAt);
                            device.sendTelemetry(
                                    sequence++,
                                    System.currentTimeMillis(),
                                    42,
                                    55,
                                    51);
                            long sleepNanos =
                                    TimeUnit.MILLISECONDS.toNanos(sampleIntervalMillis);
                            long elapsed = System.nanoTime() - sentAt;
                            if (elapsed < sleepNanos) {
                                TimeUnit.NANOSECONDS.sleep(sleepNanos - elapsed);
                            }
                        }
                    } catch (Exception exception) {
                        errorCount.incrementAndGet();
                        System.err.println(
                                "[" + account.deviceId() + "] load client failed: "
                                        + exception.getMessage());
                    } finally {
                        if (device != null) {
                            try {
                                device.close();
                            } catch (Exception ignored) {
                                // The measured connection may already be closed.
                            }
                        }
                    }
                }));
            }

            if (!clientsReady.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not all load clients connected in time");
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(durationSeconds + 30, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            if (monitor != null) {
                monitor.close();
            }
            server.close();
        }

        long persistedSamples = database.countTelemetry();
        List<Long> latencySnapshot;
        synchronized (updateLatenciesNanos) {
            latencySnapshot = new ArrayList<>(updateLatenciesNanos);
        }
        Collections.sort(latencySnapshot);
        double p50Millis = percentileMillis(latencySnapshot, 0.50);
        double p95Millis = percentileMillis(latencySnapshot, 0.95);
        double maxMillis = latencySnapshot.isEmpty()
                ? 0
                : latencySnapshot.get(latencySnapshot.size() - 1) / 1_000_000.0;
        double throughput = persistedSamples / (double) durationSeconds;

        Files.createDirectories(outputDirectory);
        String summary = """
                metric,value
                started_at,%s
                devices,%d
                duration_seconds,%d
                interval_millis,%d
                persisted_samples,%d
                monitor_updates,%d
                errors,%d
                throughput_samples_per_second,%.2f
                update_p50_ms,%.2f
                update_p95_ms,%.2f
                update_max_ms,%.2f
                """.formatted(
                Instant.now(),
                deviceCount,
                durationSeconds,
                intervalMillis,
                persistedSamples,
                updateCount.get(),
                errorCount.get(),
                throughput,
                p50Millis,
                p95Millis,
                maxMillis);
        Files.writeString(summaryFile, summary, StandardCharsets.UTF_8);

        StringBuilder samples = new StringBuilder("sample,latency_ms\n");
        for (int i = 0; i < latencySnapshot.size(); i++) {
            samples.append(i + 1)
                    .append(',')
                    .append("%.3f".formatted(latencySnapshot.get(i) / 1_000_000.0))
                    .append('\n');
        }
        Files.writeString(samplesFile, samples.toString(), StandardCharsets.UTF_8);
        System.out.println(summary);
        System.out.println("Results written to " + outputDirectory.toAbsolutePath());
    }

    private static double percentileMillis(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1))) / 1_000_000.0;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }
}

