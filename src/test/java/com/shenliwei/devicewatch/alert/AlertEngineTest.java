package com.shenliwei.devicewatch.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AlertEngineTest {
    private final AlertEngine engine = new AlertEngine();

    @Test
    void cpuRequiresThreeConsecutiveHighSamples() {
        AlertEngine.CpuCounter counter = AlertEngine.CpuCounter.initial();

        AlertEngine.CpuEvaluation first = engine.evaluateCpu(85, 80, 75, 3, counter, false);
        AlertEngine.CpuEvaluation second =
                engine.evaluateCpu(86, 80, 75, 3, first.counter(), false);
        AlertEngine.CpuEvaluation third =
                engine.evaluateCpu(87, 80, 75, 3, second.counter(), false);

        assertEquals(AlertAction.NONE, first.action());
        assertEquals(AlertAction.NONE, second.action());
        assertEquals(AlertAction.CREATE, third.action());
    }

    @Test
    void cpuCounterResetsWhenSampleIsNotAboveTrigger() {
        AlertEngine.CpuCounter first =
                engine.evaluateCpu(85, 80, 75, 3, AlertEngine.CpuCounter.initial(), false)
                .counter();
        AlertEngine.CpuEvaluation reset = engine.evaluateCpu(80, 80, 75, 3, first, false);

        assertEquals(0, reset.counter().highSamples());
        assertEquals(AlertAction.NONE, reset.action());
    }

    @Test
    void activeCpuAlertRecoversAfterThreeConsecutiveRecoverySamples() {
        AlertEngine.CpuCounter counter = AlertEngine.CpuCounter.initial();
        AlertEngine.CpuEvaluation first = engine.evaluateCpu(70, 80, 75, 3, counter, true);
        AlertEngine.CpuEvaluation second =
                engine.evaluateCpu(74, 80, 75, 3, first.counter(), true);
        AlertEngine.CpuEvaluation third =
                engine.evaluateCpu(75, 80, 75, 3, second.counter(), true);

        assertEquals(AlertAction.NONE, first.action());
        assertEquals(AlertAction.NONE, second.action());
        assertEquals(AlertAction.RESOLVE, third.action());
    }

    @Test
    void valueAboveRecoveryThresholdResetsRecoverySequence() {
        AlertEngine.CpuCounter first =
                engine.evaluateCpu(70, 80, 75, 3, AlertEngine.CpuCounter.initial(), true)
                        .counter();
        AlertEngine.CpuEvaluation reset = engine.evaluateCpu(75.1, 80, 75, 3, first, true);

        assertEquals(0, reset.counter().recoverySamples());
        assertEquals(AlertAction.NONE, reset.action());
    }

    @Test
    void temperatureUsesTriggerAndRecoveryBoundaries() {
        assertEquals(AlertAction.NONE, engine.evaluateTemperature(74.9, 75, 70, false));
        assertEquals(AlertAction.CREATE, engine.evaluateTemperature(75.0, 75, 70, false));
        assertEquals(AlertAction.NONE, engine.evaluateTemperature(70.0, 75, 70, true));
        assertEquals(AlertAction.RESOLVE, engine.evaluateTemperature(69.9, 75, 70, true));
    }

    @Test
    void memoryUsesIndependentThresholds() {
        AlertEngine.CpuCounter counter = AlertEngine.CpuCounter.initial();
        AlertEngine.CpuEvaluation first =
                engine.evaluateMemory(86, 85, 78, 3, counter, false);
        AlertEngine.CpuEvaluation second =
                engine.evaluateMemory(87, 85, 78, 3, first.counter(), false);
        AlertEngine.CpuEvaluation third =
                engine.evaluateMemory(88, 85, 78, 3, second.counter(), false);

        assertEquals(AlertAction.NONE, first.action());
        assertEquals(AlertAction.NONE, second.action());
        assertEquals(AlertAction.CREATE, third.action());
    }
}

