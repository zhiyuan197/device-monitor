package com.shenliwei.devicewatch.simulator;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

/**
 * Reads the real CPU and memory usage of the machine that runs the
 * simulator. Temperature stays synthetic: ordinary desktops do not
 * expose a thermal sensor to the JVM, which is documented in the
 * report instead of being faked silently.
 */
public final class HostMetrics {
    public record Sample(
            double cpuPercent,
            double memoryPercent,
            double diskPercent,
            double temperatureCelsius) {
    }

    private final OperatingSystemMXBean bean;

    private HostMetrics(OperatingSystemMXBean bean) {
        this.bean = bean;
    }

    public static HostMetrics local() {
        Object value = ManagementFactory.getOperatingSystemMXBean();
        return new HostMetrics(value instanceof OperatingSystemMXBean bean ? bean : null);
    }

    public Sample read(long nowMillis) {
        if (bean == null) {
            return sampleFrom(-1, 0, 0, 0, 0, nowMillis);
        }
        java.io.File workDir = new java.io.File(System.getProperty("user.dir"));
        long diskTotal = workDir.getTotalSpace();
        long diskUsed = Math.max(0, diskTotal - workDir.getUsableSpace());
        return sampleFrom(
                bean.getSystemCpuLoad(),
                bean.getTotalMemorySize(),
                bean.getFreeMemorySize(),
                diskUsed,
                diskTotal,
                nowMillis);
    }

    static Sample sampleFrom(
            double systemCpuLoad,
            long totalMemory,
            long freeMemory,
            long usedDisk,
            long totalDisk,
            long nowMillis) {
        double cpu = systemCpuLoad >= 0 ? clamp(systemCpuLoad * 100) : 0;
        double memory = totalMemory > 0
                ? clamp((totalMemory - freeMemory) * 100.0 / totalMemory)
                : 0;
        double disk = totalDisk > 0
                ? clamp(usedDisk * 100.0 / totalDisk)
                : 0;
        double temperature = 42 + 4 * Math.sin(nowMillis / 60_000.0);
        return new Sample(cpu, memory, disk, clampTemperature(temperature));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static double clampTemperature(double value) {
        return Math.max(-40, Math.min(125, value));
    }
}
