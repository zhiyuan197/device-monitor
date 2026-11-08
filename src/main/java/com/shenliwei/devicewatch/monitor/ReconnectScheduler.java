package com.shenliwei.devicewatch.monitor;

/**
 * Exponential backoff policy for automatic reconnection attempts.
 * Delay starts at 1 second, doubles per attempt, and caps at 30 seconds.
 */
final class ReconnectScheduler {
    static final long BASE_MILLIS = 1_000L;
    static final long MAX_MILLIS = 30_000L;

    private ReconnectScheduler() {
    }

    static long delayMillis(int attempt) {
        long delay = BASE_MILLIS;
        for (int i = 1; i < Math.max(1, attempt); i++) {
            delay = Math.min(delay * 2, MAX_MILLIS);
        }
        return Math.min(delay, MAX_MILLIS);
    }
}
