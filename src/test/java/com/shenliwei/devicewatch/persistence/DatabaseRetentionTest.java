package com.shenliwei.devicewatch.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shenliwei.devicewatch.domain.TelemetrySample;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the telemetry retention contract: samples older than the cutoff are
 * deleted, newer samples survive.
 */
class DatabaseRetentionTest {

    private static final long DAY_MILLIS = 86_400_000L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesOnlySamplesOlderThanCutoff() throws Exception {
        Path databasePath = temporaryDirectory.resolve("retention.db");
        Database database = new Database(databasePath);
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "monitor", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        String deviceId = credentials.devices().get(0).deviceId();
        long now = System.currentTimeMillis();

        long oldTime = now - 40L * DAY_MILLIS;
        long recentTime = now - 1_000L;
        database.insertTelemetry(new TelemetrySample(deviceId, oldTime, oldTime, 30, 40, 50, null));
        database.insertTelemetry(
                new TelemetrySample(deviceId, recentTime, recentTime, 31, 41, 51, null));

        long deleted = database.deleteTelemetryOlderThan(now - 30L * DAY_MILLIS);

        assertEquals(1, deleted);
        assertEquals(1, database.countTelemetry());
    }
}
