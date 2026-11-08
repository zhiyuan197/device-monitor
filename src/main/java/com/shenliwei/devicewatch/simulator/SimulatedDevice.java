package com.shenliwei.devicewatch.simulator;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class SimulatedDevice implements Runnable, AutoCloseable {
    private final SimulatorConfig config;
    private final SimulatorConfig.DeviceAccount account;
    private final SimulationScenario scenario;
    private final Duration duration;
    private final long intervalMillis;
    private final long latencyMillis;
    private final int deviceIndex;
    private volatile DeviceClient client;

    public SimulatedDevice(
            SimulatorConfig config,
            SimulatorConfig.DeviceAccount account,
            SimulationScenario scenario,
            Duration duration,
            long intervalMillis,
            long latencyMillis,
            int deviceIndex) {
        this.config = config;
        this.account = account;
        this.scenario = scenario;
        this.duration = duration;
        this.intervalMillis = intervalMillis;
        this.latencyMillis = latencyMillis;
        this.deviceIndex = deviceIndex;
    }

    @Override
    public void run() {
        long startedAt = System.currentTimeMillis();
        long sequence = 0;
        long nextHeartbeat = startedAt;
        boolean disconnected = false;
        boolean reconnectCycleComplete = false;
        HostMetrics hostMetrics = scenario == SimulationScenario.REAL
                ? HostMetrics.local()
                : null;
        try {
            client = DeviceClient.connect(config, account,
                    scenario == SimulationScenario.REAL,
                    config.tlsEnabled(), config.truststorePath(),
                    config.truststorePassword());
            System.out.printf("[%s] connected, scenario=%s%n", account.deviceId(), scenario);
            while (System.currentTimeMillis() - startedAt < duration.toMillis()) {
                long now = System.currentTimeMillis();
                long elapsedSeconds = (now - startedAt) / 1000;

                if (scenario == SimulationScenario.DISCONNECT && elapsedSeconds >= 8) {
                    disconnected = true;
                    System.out.printf("[%s] simulated disconnect%n", account.deviceId());
                    break;
                }

                if (scenario == SimulationScenario.RECONNECT
                        && !reconnectCycleComplete
                        && elapsedSeconds >= 8) {
                    close();
                    System.out.printf("[%s] simulated disconnect before reconnect%n", account.deviceId());
                    long remaining = duration.toMillis() - (System.currentTimeMillis() - startedAt);
                    Thread.sleep(Math.min(18_000, Math.max(0, remaining)));
                    if (System.currentTimeMillis() - startedAt >= duration.toMillis()) {
                        break;
                    }
                    client = DeviceClient.connect(config, account, false,
                            config.tlsEnabled(), config.truststorePath(),
                            config.truststorePassword());
                    reconnectCycleComplete = true;
                    nextHeartbeat = System.currentTimeMillis();
                    System.out.printf("[%s] reconnected after offline period%n", account.deviceId());
                    continue;
                }

                if (client == null) {
                    break;
                }
                if (now >= nextHeartbeat) {
                    client.sendHeartbeat(sequence++);
                    nextHeartbeat = now + 5_000;
                }

                double phase = (now / 1_000.0) + deviceIndex * 0.7;
                double cpu = 35 + 10 * Math.sin(phase);
                double memory = 50 + 8 * Math.cos(phase / 2);
                double temperature = 52 + 4 * Math.sin(phase / 3);
                Double disk = 55 + 6 * Math.sin(phase / 4);

                if (hostMetrics != null) {
                    HostMetrics.Sample real = hostMetrics.read(now);
                    cpu = real.cpuPercent();
                    memory = real.memoryPercent();
                    disk = real.diskPercent();
                    temperature = real.temperatureCelsius();
                }

                if (scenario == SimulationScenario.CPU && elapsedSeconds >= 4) {
                    cpu = 84 + (elapsedSeconds % 5);
                }
                if (scenario == SimulationScenario.MEMORY && elapsedSeconds >= 4) {
                    memory = 87 + (elapsedSeconds % 5);
                }
                if (scenario == SimulationScenario.TEMPERATURE && elapsedSeconds >= 5) {
                    temperature = 77 + (elapsedSeconds % 3);
                }
                if (scenario == SimulationScenario.OSCILLATION) {
                    cpu = 57.5 + 37.5 * Math.sin(elapsedSeconds / 4.5);
                }

                client.sendTelemetry(
                        sequence++,
                        collectedAt(now, latencyMillis),
                        clamp(cpu),
                        clamp(memory),
                        clampTemperature(temperature),
                        disk);
                Thread.sleep(intervalMillis);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!disconnected) {
                System.err.printf("[%s] simulator stopped: %s%n", account.deviceId(), exception.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Shifts the collection timestamp back to simulate transit time on a
     * real network. Clearly labeled as a demo value, never mixed into
     * real-device measurements.
     */
    static long collectedAt(long now, long latencyMillis) {
        return Math.max(0, now - Math.max(0, latencyMillis));
    }

    @Override
    public void close() {
        DeviceClient current = client;
        client = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // The simulator may already have lost the connection.
            }
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static double clampTemperature(double value) {
        return Math.max(-40, Math.min(125, value));
    }
}

