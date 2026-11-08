package com.shenliwei.devicewatch.simulator;

public enum SimulationScenario {
    NORMAL,
    CPU,
    MEMORY,
    TEMPERATURE,
    DISCONNECT,
    RECONNECT,
    OSCILLATION,
    REAL,
    MIXED;

    public static SimulationScenario parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "scenario must be NORMAL, CPU, MEMORY, TEMPERATURE, DISCONNECT, "
                            + "RECONNECT, OSCILLATION, REAL, or MIXED");
        }
    }

    public SimulationScenario forDevice(int index) {
        if (this != MIXED) {
            return this;
        }
        return switch (index % 7) {
            case 1 -> CPU;
            case 2 -> MEMORY;
            case 3 -> TEMPERATURE;
            case 4 -> DISCONNECT;
            case 5 -> RECONNECT;
            case 6 -> OSCILLATION;
            default -> NORMAL;
        };
    }
}

