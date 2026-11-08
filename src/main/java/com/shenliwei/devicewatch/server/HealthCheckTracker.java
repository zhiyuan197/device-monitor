package com.shenliwei.devicewatch.server;

/**
 * Pure decision logic for service health checks: consecutive failures
 * above the threshold create an alert once; a later success recovers it.
 */
final class HealthCheckTracker {
    enum Action { NONE, FAIL, RECOVER }

    private HealthCheckTracker() {
    }

    static Action evaluate(int consecutiveFailures, boolean wasFailing, boolean success,
            int threshold) {
        if (success) {
            return wasFailing ? Action.RECOVER : Action.NONE;
        }
        int next = consecutiveFailures + 1;
        if (!wasFailing && next >= threshold) {
            return Action.FAIL;
        }
        return Action.NONE;
    }
}
