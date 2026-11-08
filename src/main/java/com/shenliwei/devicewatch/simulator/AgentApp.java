package com.shenliwei.devicewatch.simulator;

import java.nio.file.Path;
import java.time.Duration;

/**
 * amon-style agent: a long-running process that reports this machine's
 * real system metrics as one monitored device.
 */
public final class AgentApp {
    private AgentApp() {
    }

    public static void run(String[] args) throws Exception {
        Path configPath = Path.of("config", "demo.properties");
        long intervalMillis = 1_000;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = Path.of(requireValue(args, ++i, "--config"));
                case "--interval-millis" -> intervalMillis = Long.parseLong(
                        requireValue(args, ++i, "--interval-millis"));
                case "--help", "-h" -> {
                    System.out.println("agent [--config config/demo.properties] "
                            + "[--interval-millis 1000]");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown agent option: " + args[i]);
            }
        }
        SimulatorConfig config = SimulatorConfig.load(configPath);
        if (config.devices().isEmpty()) {
            throw new IllegalArgumentException("Agent config has no device account");
        }
        SimulatedDevice agent = new SimulatedDevice(
                config,
                config.devices().get(0),
                SimulationScenario.REAL,
                Duration.ofDays(3650),
                Math.max(100, intervalMillis),
                0,
                0);
        agent.run();
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }
}
