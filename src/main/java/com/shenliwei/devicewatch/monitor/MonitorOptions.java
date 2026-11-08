package com.shenliwei.devicewatch.monitor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parsed command line options of the monitor entry point.
 */
record MonitorOptions(
        Path configPath,
        Path demoConfigPath,
        boolean demoMode,
        boolean stopDemoOnExit) {

    static MonitorOptions parse(String[] args) {
        Path configPath = Path.of("config", "server.properties");
        Path demoConfigPath = Path.of("config", "demo.properties");
        boolean demoMode = false;
        boolean stopDemoOnExit = false;
        if (!Files.exists(configPath)) {
            Path example = Path.of("config", "server.properties.example");
            if (Files.exists(example)) {
                configPath = example;
            }
        }
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" ->
                        configPath = Path.of(requireValue(args, ++i, "--config"));
                case "--demo-config" ->
                        demoConfigPath = Path.of(requireValue(args, ++i, "--demo-config"));
                case "--demo" -> demoMode = true;
                case "--demo-stop-on-exit" -> stopDemoOnExit = true;
                case "--help", "-h" -> {
                    throw new HelpRequestedException();
                }
                default -> throw new IllegalArgumentException(
                        "Unknown monitor option: " + args[i]);
            }
        }
        return new MonitorOptions(configPath, demoConfigPath, demoMode, stopDemoOnExit);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    static final class HelpRequestedException extends RuntimeException {
    }
}
