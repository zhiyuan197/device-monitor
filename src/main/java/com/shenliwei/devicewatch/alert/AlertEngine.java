package com.shenliwei.devicewatch.alert;

/**
 * Pure threshold logic with hysteresis and consecutive-sample requirements.
 * Thresholds are supplied per evaluation so that each device can use its
 * own rules while inactive devices fall back to server-wide defaults.
 */
public final class AlertEngine {
    public CpuEvaluation evaluateCpu(
            double value,
            double trigger,
            double recover,
            int requiredSamples,
            CpuCounter counter,
            boolean active) {
        requireThresholds(trigger, recover, requiredSamples);
        return evaluateCounter(value, trigger, recover, requiredSamples, counter, active);
    }

    public CpuEvaluation evaluateMemory(
            double value,
            double trigger,
            double recover,
            int requiredSamples,
            CpuCounter counter,
            boolean active) {
        requireThresholds(trigger, recover, requiredSamples);
        return evaluateCounter(value, trigger, recover, requiredSamples, counter, active);
    }

    public AlertAction evaluateTemperature(
            double temperature,
            double trigger,
            double recover,
            boolean active) {
        requireThresholds(trigger, recover, 1);
        if (active) {
            return temperature < recover ? AlertAction.RESOLVE : AlertAction.NONE;
        }
        return temperature >= trigger ? AlertAction.CREATE : AlertAction.NONE;
    }

    private static CpuEvaluation evaluateCounter(
            double value,
            double trigger,
            double recover,
            int requiredSamples,
            CpuCounter counter,
            boolean active) {
        if (active) {
            int recovery = value <= recover ? counter.recoverySamples() + 1 : 0;
            AlertAction action =
                    recovery >= requiredSamples ? AlertAction.RESOLVE : AlertAction.NONE;
            return new CpuEvaluation(
                    action == AlertAction.RESOLVE ? CpuCounter.initial() : new CpuCounter(0, recovery),
                    action);
        }
        int high = value > trigger ? counter.highSamples() + 1 : 0;
        AlertAction action = high >= requiredSamples ? AlertAction.CREATE : AlertAction.NONE;
        return new CpuEvaluation(
                action == AlertAction.CREATE ? CpuCounter.initial() : new CpuCounter(high, 0),
                action);
    }

    private static void requireThresholds(double trigger, double recover, int requiredSamples) {
        if (trigger <= recover) {
            throw new IllegalArgumentException("Trigger must exceed recovery threshold");
        }
        if (requiredSamples <= 0) {
            throw new IllegalArgumentException("requiredSamples must be positive");
        }
    }

    public record CpuCounter(int highSamples, int recoverySamples) {
        public CpuCounter {
            if (highSamples < 0 || recoverySamples < 0) {
                throw new IllegalArgumentException("CPU counters must be non-negative");
            }
        }

        public static CpuCounter initial() {
            return new CpuCounter(0, 0);
        }
    }

    public record CpuEvaluation(CpuCounter counter, AlertAction action) {
    }
}

