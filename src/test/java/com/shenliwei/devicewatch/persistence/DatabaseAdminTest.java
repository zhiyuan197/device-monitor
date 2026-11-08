package com.shenliwei.devicewatch.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.domain.AlertRecord;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.domain.AlertSeverity;
import com.shenliwei.devicewatch.domain.AuditEvent;
import com.shenliwei.devicewatch.domain.DeviceInfo;
import com.shenliwei.devicewatch.domain.DeviceRules;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseAdminTest {

    @TempDir
    Path temporaryDirectory;

    private static final DeviceRules DEFAULT_RULES =
            new DeviceRules(80, 75, 3, 85, 78, 3, 75, 70);

    @Test
    void registersDevicesMaintainsRulesAndReissuesCredentials() throws Exception {
        Database database = new Database(temporaryDirectory.resolve("admin.db"));
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "operator", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        long now = System.currentTimeMillis();

        String credential = database.registerDevice(
                "DEV-010", "新设备", "机房 A 区", DEFAULT_RULES, now);
        assertFalse(credential.isBlank());
        assertTrue(database.authenticateDevice("DEV-010", credential.toCharArray()));

        List<DeviceInfo> devices = database.listDeviceInfos(DEFAULT_RULES);
        assertEquals(2, devices.size());
        DeviceInfo registered = devices.stream()
                .filter(device -> "DEV-010".equals(device.deviceId()))
                .findFirst()
                .orElseThrow();
        assertEquals("机房 A 区", registered.location());
        assertEquals(85, registered.rules().memoryTrigger());

        DeviceRules stricter = new DeviceRules(70, 65, 2, 80, 75, 2, 70, 65);
        database.updateDeviceRules("DEV-010", stricter, now + 1);
        assertEquals(stricter, database.findDeviceRules("DEV-010").orElseThrow());

        String replacement = database.issueNewDeviceCredential("DEV-010");
        assertFalse(database.authenticateDevice("DEV-010", credential.toCharArray()));
        assertTrue(database.authenticateDevice("DEV-010", replacement.toCharArray()));

        database.updateDevice("DEV-010", "重命名设备", "机房 B 区");
        database.setDeviceEnabled("DEV-010", false);
        assertFalse(database.authenticateDevice("DEV-010", replacement.toCharArray()));
        assertEquals("重命名设备", database.listDeviceInfos(DEFAULT_RULES).stream()
                .filter(device -> "DEV-010".equals(device.deviceId()))
                .findFirst()
                .orElseThrow()
                .displayName());
    }

    @Test
    void acknowledgesWithOperatorAndCommentAndRecordsAuditEvents() throws Exception {
        Database database = new Database(temporaryDirectory.resolve("audit.db"));
        database.initialize();
        DemoCredentials credentials = database.seedDemoData(
                1, "operator", "operator-password".toCharArray(), null, "127.0.0.1", 5050);
        String deviceId = credentials.devices().get(0).deviceId();
        long now = System.currentTimeMillis();

        AlertRecord alert = database.createAlert(
                deviceId, AlertRuleType.CPU, AlertSeverity.CRITICAL, 92.0, now);
        AlertRecord acknowledged =
                database.acknowledgeAlert(alert.alertId(), now + 1, "monitor", "已联系机房检查");
        assertEquals("monitor", acknowledged.acknowledgedBy());
        assertEquals("已联系机房检查", acknowledged.ackComment());
        assertEquals(AlertSeverity.CRITICAL, acknowledged.severity());

        database.insertAuditEvent("monitor", "ACK_ALERT", deviceId, "备注 测试", now + 2);
        database.insertAuditEvent("monitor", "LOGIN", "MONITOR", "登录成功", now + 3);
        List<AuditEvent> events = database.queryAuditLog(10);
        assertEquals(2, events.size());
        assertEquals("LOGIN", events.get(0).action());

        database.changeOperatorPassword("operator", "new-password-123".toCharArray());
        assertTrue(database.authenticateOperator("operator", "new-password-123".toCharArray()));
        assertFalse(database.authenticateOperator("operator", "operator-password".toCharArray()));

        assertEquals(1, database.countActiveAlerts());
        assertNotNull(database.queryAlerts(
                Optional.empty(), Optional.of(AlertRuleType.CPU), Optional.empty(),
                0, now + 10, 10).get(0).acknowledgedBy());
    }
}
