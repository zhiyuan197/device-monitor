package com.shenliwei.devicewatch.persistence;

import com.shenliwei.devicewatch.common.AppConfig;
import java.nio.file.Path;

public final class SeedDemoApp {
    private SeedDemoApp() {
    }

    public static void run(String[] args) throws Exception {
        AppConfig defaults = AppConfig.defaults();
        Path databasePath = defaults.databasePath();
        Path outputPath = Path.of("config", "demo.properties");
        int deviceCount = 4;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--db" -> databasePath = Path.of(requireValue(args, ++i, "--db"));
                case "--output" -> outputPath = Path.of(requireValue(args, ++i, "--output"));
                case "--devices" ->
                        deviceCount = Integer.parseInt(requireValue(args, ++i, "--devices"));
                case "--help", "-h" -> {
                    System.out.println(
                            "seed-demo [--db data/device-monitor.db] [--devices 4] "
                                    + "[--output config/demo.properties]");
                    return;
                }
                default -> throw new IllegalArgumentException(
                        "Unknown seed-demo option: " + args[i]);
            }
        }

        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                deviceCount,
                "monitor",
                null,
                outputPath,
                "127.0.0.1",
                AppConfig.DEFAULT_PORT);

        System.out.println("Demo data created.");
        System.out.println("Monitor username: " + credentials.operatorUsername());
        System.out.println("Monitor password: " + credentials.operatorPassword());
        System.out.println("Credential file: " + outputPath.toAbsolutePath());
        for (DemoCredentials.DeviceSecret device : credentials.devices()) {
            System.out.println(
                    "Device " + device.deviceId() + " credential: " + device.credential());
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }
}

