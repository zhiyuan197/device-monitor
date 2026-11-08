package com.shenliwei.devicewatch.simulator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class SimulatorApp {
    private SimulatorApp() {
    }

    public static void run(String[] args) throws Exception {
        Path configPath = Path.of("config", "demo.properties");
        SimulationScenario scenario = SimulationScenario.MIXED;
        Duration duration = Duration.ofSeconds(60);
        long intervalMillis = 1_000;
        Integer requestedDevices = null;
        int offset = 0;
        long latencyMillis = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = Path.of(requireValue(args, ++i, "--config"));
                case "--scenario" -> scenario =
                        SimulationScenario.parse(requireValue(args, ++i, "--scenario"));
                case "--duration-seconds" -> duration = Duration.ofSeconds(
                        Long.parseLong(requireValue(args, ++i, "--duration-seconds")));
                case "--interval-millis" -> intervalMillis = Long.parseLong(
                        requireValue(args, ++i, "--interval-millis"));
                case "--devices" -> requestedDevices =
                        Integer.parseInt(requireValue(args, ++i, "--devices"));
                case "--offset" -> offset =
                        Integer.parseInt(requireValue(args, ++i, "--offset"));
                case "--latency-millis" -> latencyMillis =
                        Long.parseLong(requireValue(args, ++i, "--latency-millis"));
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown simulator option: " + args[i]);
            }
        }

        SimulatorConfig config = SimulatorConfig.load(configPath);
        int count = requestedDevices == null
                ? config.devices().size()
                : Math.min(requestedDevices, config.devices().size());
        if (count < 1 || duration.isNegative() || duration.isZero() || intervalMillis < 100) {
            throw new IllegalArgumentException("Invalid simulator duration, count, or interval");
        }
        validateSelection(count, offset, config.devices().size());
        validateLatency(latencyMillis);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(count, 50));
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                SimulatedDevice device = new SimulatedDevice(
                        config,
                        config.devices().get(offset + i),
                        scenario.forDevice(i),
                        duration,
                        intervalMillis,
                        latencyMillis,
                        i);
                futures.add(executor.submit(device));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    static void validateSelection(int count, int offset, int total) {
        if (count < 0 || offset < 0 || offset + count > total) {
            throw new IllegalArgumentException(
                    "device selection exceeds configured devices: offset=" + offset
                            + " count=" + count + " total=" + total);
        }
    }

    static void validateLatency(long latencyMillis) {
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("--latency-millis must be >= 0");
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    public static void printUsage() {
        System.out.println(
                "simulate [--config config/demo.properties] [--devices 4] "
                        + "[--scenario MIXED|NORMAL|CPU|MEMORY|TEMPERATURE|DISCONNECT|"
                        + "RECONNECT|OSCILLATION|REAL] "
                        + "[--offset 0] [--latency-millis 0] "
                        + "[--duration-seconds 60] [--interval-millis 1000]");
    }
}

