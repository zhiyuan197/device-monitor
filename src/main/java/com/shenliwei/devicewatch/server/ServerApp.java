package com.shenliwei.devicewatch.server;

import com.shenliwei.devicewatch.common.AppConfig;
import com.shenliwei.devicewatch.persistence.Database;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerApp {
    private ServerApp() {
    }

    public static void run(String[] args) throws Exception {
        Path configPath = Path.of("config", "server.properties");
        if (!Files.exists(configPath)) {
            Path example = Path.of("config", "server.properties.example");
            if (Files.exists(example)) {
                configPath = example;
            }
        }
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i])) {
                configPath = Path.of(requireValue(args, ++i, "--config"));
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                System.out.println("server [--config config/server.properties]");
                return;
            } else {
                throw new IllegalArgumentException("Unknown server option: " + args[i]);
            }
        }

        AppConfig config = AppConfig.load(configPath);
        MonitorServer server = new MonitorServer(config, new Database(config.databasePath()));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown-hook"));
        server.start();
        System.out.printf(
                "Device monitor server listening on %s:%d; database=%s%n",
                config.host(), server.port(), config.databasePath().toAbsolutePath());
        server.awaitTermination();
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }
}

