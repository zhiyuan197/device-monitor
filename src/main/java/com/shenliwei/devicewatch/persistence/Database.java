package com.shenliwei.devicewatch.persistence;

import com.shenliwei.devicewatch.common.PasswordHasher;
import com.shenliwei.devicewatch.domain.AlertRecord;
import com.shenliwei.devicewatch.domain.AlertRuleType;
import com.shenliwei.devicewatch.domain.AlertSeverity;
import com.shenliwei.devicewatch.domain.AlertStatus;
import com.shenliwei.devicewatch.domain.AuditEvent;
import com.shenliwei.devicewatch.domain.DeviceCredential;
import com.shenliwei.devicewatch.domain.DeviceHeader;
import com.shenliwei.devicewatch.domain.DeviceInfo;
import com.shenliwei.devicewatch.domain.DeviceRules;
import com.shenliwei.devicewatch.domain.OperatorCredential;
import com.shenliwei.devicewatch.domain.ServiceCheck;
import com.shenliwei.devicewatch.domain.TelemetrySample;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * SQLite-backed storage for credentials, telemetry, and alert history.
 */
public final class Database {
    private final Path path;
    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom;

    public Database(Path path) {
        this(path, new PasswordHasher(), new SecureRandom());
    }

    Database(Path path, PasswordHasher passwordHasher, SecureRandom secureRandom) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public Path path() {
        return path;
    }

    public void initialize() throws SQLException, IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Connection connection = open();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
        }

        String schema;
        try (InputStream input = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (input == null) {
                throw new IOException("Classpath resource /db/schema.sql is missing");
            }
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection connection = open();
             Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";\\s*\\R")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
            migrateLegacySchema(connection);
        }
    }

    public boolean authenticateDevice(String deviceId, char[] credential) throws SQLException {
        return findDeviceCredential(deviceId)
                .filter(DeviceCredential::enabled)
                .map(DeviceCredential::credentialHash)
                .map(hash -> passwordHasher.verify(credential, hash))
                .orElse(false);
    }

    public boolean authenticateOperator(String username, char[] password) throws SQLException {
        return findOperatorCredential(username)
                .filter(OperatorCredential::enabled)
                .map(OperatorCredential::passwordHash)
                .map(hash -> passwordHasher.verify(password, hash))
                .orElse(false);
    }

    public Optional<DeviceCredential> findDeviceCredential(String deviceId) throws SQLException {
        String sql =
                "SELECT device_id, display_name, credential_hash, enabled "
                        + "FROM devices WHERE device_id = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DeviceCredential(
                        result.getString("device_id"),
                        result.getString("display_name"),
                        result.getString("credential_hash"),
                        result.getInt("enabled") == 1));
            }
        }
    }

    public Optional<OperatorCredential> findOperatorCredential(String username)
            throws SQLException {
        String sql = "SELECT username, password_hash, enabled FROM operators WHERE username = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OperatorCredential(
                        result.getString("username"),
                        result.getString("password_hash"),
                        result.getInt("enabled") == 1));
            }
        }
    }

    public List<DeviceHeader> listDevices() throws SQLException {
        String sql = "SELECT device_id, display_name FROM devices ORDER BY device_id";
        List<DeviceHeader> devices = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                devices.add(new DeviceHeader(result.getString("device_id"), result.getString("display_name")));
            }
        }
        return devices;
    }

    public List<DeviceInfo> listDeviceInfos(DeviceRules defaultRules) throws SQLException {
        String sql =
                """
                SELECT d.device_id, d.display_name, d.location, d.enabled, d.created_at,
                       r.cpu_trigger, r.cpu_recover, r.cpu_samples,
                       r.memory_trigger, r.memory_recover, r.memory_samples,
                       r.temp_trigger, r.temp_recover
                FROM devices d
                LEFT JOIN device_rules r ON r.device_id = d.device_id
                ORDER BY d.device_id
                """;
        List<DeviceInfo> devices = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                double cpuTrigger = result.getDouble("cpu_trigger");
                DeviceRules rules = result.wasNull()
                        ? defaultRules
                        : new DeviceRules(
                                cpuTrigger,
                                result.getDouble("cpu_recover"),
                                result.getInt("cpu_samples"),
                                result.getDouble("memory_trigger"),
                                result.getDouble("memory_recover"),
                                result.getInt("memory_samples"),
                                result.getDouble("temp_trigger"),
                                result.getDouble("temp_recover"));
                devices.add(new DeviceInfo(
                        result.getString("device_id"),
                        result.getString("display_name"),
                        result.getString("location"),
                        result.getInt("enabled") == 1,
                        result.getLong("created_at"),
                        rules));
            }
        }
        return devices;
    }

    /**
     * Registers a new device and returns its generated plaintext credential.
     */
    public String registerDevice(
            String deviceId,
            String displayName,
            String location,
            DeviceRules rules,
            long now)
            throws SQLException {
        String credential = new String(randomSecret());
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                insertDevice(connection, deviceId, displayName, location,
                        passwordHasher.hash(credential.toCharArray()), now);
                insertDeviceRules(connection, deviceId, rules, now);
                connection.commit();
                return credential;
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void updateDevice(String deviceId, String displayName, String location)
            throws SQLException {
        String sql = "UPDATE devices SET display_name = ?, location = ? WHERE device_id = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            statement.setString(2, location);
            statement.setString(3, deviceId);
            statement.executeUpdate();
        }
    }

    public void setDeviceEnabled(String deviceId, boolean enabled) throws SQLException {
        String sql = "UPDATE devices SET enabled = ? WHERE device_id = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setString(2, deviceId);
            statement.executeUpdate();
        }
    }

    public Optional<DeviceRules> findDeviceRules(String deviceId) throws SQLException {
        String sql =
                """
                SELECT cpu_trigger, cpu_recover, cpu_samples, memory_trigger,
                       memory_recover, memory_samples, temp_trigger, temp_recover
                FROM device_rules WHERE device_id = ?
                """;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DeviceRules(
                        result.getDouble("cpu_trigger"),
                        result.getDouble("cpu_recover"),
                        result.getInt("cpu_samples"),
                        result.getDouble("memory_trigger"),
                        result.getDouble("memory_recover"),
                        result.getInt("memory_samples"),
                        result.getDouble("temp_trigger"),
                        result.getDouble("temp_recover")));
            }
        }
    }

    public void updateDeviceRules(String deviceId, DeviceRules rules, long now)
            throws SQLException {
        String sql =
                """
                INSERT INTO device_rules(
                    device_id, cpu_trigger, cpu_recover, cpu_samples, memory_trigger,
                    memory_recover, memory_samples, temp_trigger, temp_recover, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    cpu_trigger = excluded.cpu_trigger,
                    cpu_recover = excluded.cpu_recover,
                    cpu_samples = excluded.cpu_samples,
                    memory_trigger = excluded.memory_trigger,
                    memory_recover = excluded.memory_recover,
                    memory_samples = excluded.memory_samples,
                    temp_trigger = excluded.temp_trigger,
                    temp_recover = excluded.temp_recover,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setDouble(2, rules.cpuTrigger());
            statement.setDouble(3, rules.cpuRecover());
            statement.setInt(4, rules.cpuRequiredSamples());
            statement.setDouble(5, rules.memoryTrigger());
            statement.setDouble(6, rules.memoryRecover());
            statement.setInt(7, rules.memoryRequiredSamples());
            statement.setDouble(8, rules.temperatureTrigger());
            statement.setDouble(9, rules.temperatureRecover());
            statement.setLong(10, now);
            statement.executeUpdate();
        }
    }

    /**
     * Replaces a device credential and returns the new plaintext value.
     */
    public String issueNewDeviceCredential(String deviceId) throws SQLException {
        String credential = new String(randomSecret());
        String sql = "UPDATE devices SET credential_hash = ? WHERE device_id = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHasher.hash(credential.toCharArray()));
            statement.setString(2, deviceId);
            statement.executeUpdate();
        }
        return credential;
    }

    public void changeOperatorPassword(String username, char[] newPassword)
            throws SQLException {
        String sql = "UPDATE operators SET password_hash = ? WHERE username = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHasher.hash(newPassword));
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }

    public void insertAuditEvent(
            String actor, String action, String target, String detail, long createdAt)
            throws SQLException {
        String sql =
                """
                INSERT INTO audit_log(actor, action, target, detail, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actor);
            statement.setString(2, action);
            statement.setString(3, target);
            statement.setString(4, detail);
            statement.setLong(5, createdAt);
            statement.executeUpdate();
        }
    }

    public List<AuditEvent> queryAuditLog(int limit) throws SQLException {
        String sql =
                "SELECT id, actor, action, target, detail, created_at "
                        + "FROM audit_log ORDER BY created_at DESC, id DESC LIMIT ?";
        List<AuditEvent> events = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    events.add(new AuditEvent(
                            result.getLong("id"),
                            result.getString("actor"),
                            result.getString("action"),
                            result.getString("target"),
                            result.getString("detail"),
                            result.getLong("created_at")));
                }
            }
        }
        return events;
    }

    public long countActiveAlerts() throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM alerts WHERE status = 'ACTIVE'");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    public Map<String, TelemetrySample> latestTelemetryByDevice() throws SQLException {
        String sql =
                """
                SELECT t.device_id, t.collected_at, t.received_at, t.cpu_percent,
                       t.memory_percent, t.temperature_celsius, t.disk_percent
                FROM telemetry t
                INNER JOIN (
                    SELECT device_id, MAX(received_at) AS max_received_at
                    FROM telemetry
                    GROUP BY device_id
                ) latest
                ON latest.device_id = t.device_id
                AND latest.max_received_at = t.received_at
                """;
        Map<String, TelemetrySample> samples = new HashMap<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                TelemetrySample sample = mapTelemetry(result);
                samples.put(sample.deviceId(), sample);
            }
        }
        return samples;
    }

    public long insertTelemetry(TelemetrySample sample) throws SQLException {
        try (Connection connection = open()) {
            return insertTelemetry(connection, sample);
        }
    }

    public List<AlertRecord> saveTelemetryAndAlertChanges(
            TelemetrySample sample, List<AlertChange> alertChanges) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                insertTelemetry(connection, sample);
                List<AlertRecord> changedAlerts = new ArrayList<>();
                for (AlertChange change : alertChanges) {
                    if (change.isCreate()) {
                        changedAlerts.add(createAlert(
                                connection,
                                sample.deviceId(),
                                change.ruleType(),
                                change.severity(),
                                change.triggerValue(),
                                change.eventAt()));
                    } else {
                        changedAlerts.add(resolveAlert(
                                connection, change.alertIdToResolve(), change.eventAt()));
                    }
                }
                connection.commit();
                return List.copyOf(changedAlerts);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static long insertTelemetry(Connection connection, TelemetrySample sample)
            throws SQLException {
        String sql =
                """
                INSERT INTO telemetry(
                    device_id, collected_at, received_at, cpu_percent,
                    memory_percent, temperature_celsius, disk_percent
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sample.deviceId());
            statement.setLong(2, sample.collectedAt());
            statement.setLong(3, sample.receivedAt());
            statement.setDouble(4, sample.cpuPercent());
            statement.setDouble(5, sample.memoryPercent());
            statement.setDouble(6, sample.temperatureCelsius());
            if (sample.diskPercent() == null) {
                statement.setNull(7, java.sql.Types.REAL);
            } else {
                statement.setDouble(7, sample.diskPercent());
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Telemetry insert did not return an id");
                }
                return keys.getLong(1);
            }
        }
    }

    public List<AlertRecord> activeAlerts() throws SQLException {
        String sql =
                """
                SELECT id, device_id, rule_type, severity, status, trigger_value,
                       triggered_at, resolved_at, acknowledged_at, acknowledged_by, ack_comment
                FROM alerts
                WHERE status = 'ACTIVE'
                ORDER BY triggered_at DESC, id DESC
                """;
        List<AlertRecord> alerts = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                alerts.add(mapAlert(result));
            }
        }
        return alerts;
    }

    public Optional<AlertRecord> findActiveAlert(String deviceId, AlertRuleType ruleType)
            throws SQLException {
        try (Connection connection = open()) {
            return findActiveAlert(connection, deviceId, ruleType);
        }
    }

    private static Optional<AlertRecord> findActiveAlert(
            Connection connection, String deviceId, AlertRuleType ruleType) throws SQLException {
        String sql =
                """
                SELECT id, device_id, rule_type, severity, status, trigger_value,
                       triggered_at, resolved_at, acknowledged_at, acknowledged_by, ack_comment
                FROM alerts
                WHERE device_id = ? AND rule_type = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setString(2, ruleType.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapAlert(result)) : Optional.empty();
            }
        }
    }

    public AlertRecord createAlert(
            String deviceId,
            AlertRuleType ruleType,
            Double triggerValue,
            long triggeredAt)
            throws SQLException {
        return createAlert(deviceId, ruleType, AlertSeverity.WARNING, triggerValue, triggeredAt);
    }

    public AlertRecord createAlert(
            String deviceId,
            AlertRuleType ruleType,
            AlertSeverity severity,
            Double triggerValue,
            long triggeredAt)
            throws SQLException {
        String sql =
                """
                INSERT INTO alerts(
                    device_id, rule_type, severity, status, trigger_value, triggered_at, resolved_at
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL)
                """;
        try (Connection connection = open();
             PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, deviceId);
            statement.setString(2, ruleType.name());
            statement.setString(3, severity.name());
            if (triggerValue == null) {
                statement.setNull(4, java.sql.Types.REAL);
            } else {
                statement.setDouble(4, triggerValue);
            }
            statement.setLong(5, triggeredAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Alert insert did not return an id");
                }
                long id = keys.getLong(1);
                return new AlertRecord(
                        id,
                        deviceId,
                        ruleType,
                        severity,
                        AlertStatus.ACTIVE,
                        triggerValue,
                        triggeredAt,
                        null,
                        null,
                        null,
                        null);
            }
        } catch (SQLException exception) {
            Optional<AlertRecord> existing = findActiveAlert(deviceId, ruleType);
            if (existing.isPresent()) {
                return existing.get();
            }
            throw exception;
        }
    }

    private static AlertRecord createAlert(
            Connection connection,
            String deviceId,
            AlertRuleType ruleType,
            AlertSeverity severity,
            Double triggerValue,
            long triggeredAt)
            throws SQLException {
        String sql =
                """
                INSERT INTO alerts(
                    device_id, rule_type, severity, status, trigger_value, triggered_at, resolved_at
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL)
                """;
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, deviceId);
            statement.setString(2, ruleType.name());
            statement.setString(3, severity.name());
            if (triggerValue == null) {
                statement.setNull(4, java.sql.Types.REAL);
            } else {
                statement.setDouble(4, triggerValue);
            }
            statement.setLong(5, triggeredAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Alert insert did not return an id");
                }
                return new AlertRecord(
                        keys.getLong(1),
                        deviceId,
                        ruleType,
                        severity,
                        AlertStatus.ACTIVE,
                        triggerValue,
                        triggeredAt,
                        null,
                        null,
                        null,
                        null);
            }
        } catch (SQLException exception) {
            Optional<AlertRecord> existing =
                    findActiveAlert(connection, deviceId, ruleType);
            if (existing.isPresent()) {
                return existing.get();
            }
            throw exception;
        }
    }

    public AlertRecord resolveAlert(long alertId, long resolvedAt) throws SQLException {
        try (Connection connection = open()) {
            return resolveAlert(connection, alertId, resolvedAt);
        }
    }

    private static AlertRecord resolveAlert(
            Connection connection, long alertId, long resolvedAt) throws SQLException {
        String update =
                """
                UPDATE alerts
                SET status = 'RESOLVED', resolved_at = ?
                WHERE id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, resolvedAt);
            statement.setLong(2, alertId);
            statement.executeUpdate();
        }

        String select =
                """
                SELECT id, device_id, rule_type, severity, status, trigger_value,
                       triggered_at, resolved_at, acknowledged_at, acknowledged_by, ack_comment
                FROM alerts WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setLong(1, alertId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Alert not found: " + alertId);
                }
                return mapAlert(result);
            }
        }
    }

    public List<TelemetrySample> queryTelemetry(
            String deviceId, long fromMillis, long toMillis, int limit) throws SQLException {
        String sql =
                """
                SELECT device_id, collected_at, received_at, cpu_percent,
                       memory_percent, temperature_celsius, disk_percent
                FROM telemetry
                WHERE device_id = ? AND received_at >= ? AND received_at <= ?
                ORDER BY received_at ASC, id ASC
                LIMIT ?
                """;
        List<TelemetrySample> samples = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setLong(2, fromMillis);
            statement.setLong(3, toMillis);
            statement.setInt(4, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    samples.add(mapTelemetry(result));
                }
            }
        }
        return samples;
    }

    public AlertRecord acknowledgeAlert(long alertId, long acknowledgedAt) throws SQLException {
        return acknowledgeAlert(alertId, acknowledgedAt, null, null);
    }

    public AlertRecord acknowledgeAlert(
            long alertId, long acknowledgedAt, String acknowledgedBy, String ackComment)
            throws SQLException {
        String update =
                """
                UPDATE alerts
                SET acknowledged_at = COALESCE(acknowledged_at, ?),
                    acknowledged_by = COALESCE(acknowledged_by, ?),
                    ack_comment = COALESCE(ack_comment, ?)
                WHERE id = ? AND status = 'ACTIVE'
                """;
        try (Connection connection = open()) {
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setLong(1, acknowledgedAt);
                statement.setString(2, acknowledgedBy);
                statement.setString(3, ackComment);
                statement.setLong(4, alertId);
                statement.executeUpdate();
            }
            return findAlertById(connection, alertId);
        }
    }

    public long countTelemetry() throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(*) FROM telemetry");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    public long deleteTelemetryOlderThan(long cutoffMillis) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement =
                     connection.prepareStatement(
                             "DELETE FROM telemetry WHERE collected_at < ?")) {
            statement.setLong(1, cutoffMillis);
            return statement.executeUpdate();
        }
    }

    public ServiceCheck upsertServiceCheck(
            String name,
            String host,
            int port,
            int intervalMillis,
            int failThreshold,
            long now)
            throws SQLException {
        String sql =
                """
                INSERT INTO service_checks(
                    name, host, port, interval_millis, fail_threshold, enabled, created_at
                ) VALUES (?, ?, ?, ?, ?, 1, ?)
                ON CONFLICT(name) DO UPDATE SET
                    host = excluded.host,
                    port = excluded.port,
                    interval_millis = excluded.interval_millis,
                    fail_threshold = excluded.fail_threshold,
                    enabled = 1
                """;
        try (Connection connection = open()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                statement.setString(2, host);
                statement.setInt(3, port);
                statement.setInt(4, intervalMillis);
                statement.setInt(5, failThreshold);
                statement.setLong(6, now);
                statement.executeUpdate();
            }
            return findServiceCheckByName(connection, name).orElseThrow(
                    () -> new SQLException("Service check upsert failed: " + name));
        }
    }

    public List<ServiceCheck> listServiceChecks() throws SQLException {
        String sql =
                "SELECT id, name, host, port, interval_millis, fail_threshold, enabled, created_at "
                        + "FROM service_checks ORDER BY name ASC";
        List<ServiceCheck> checks = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                checks.add(mapServiceCheck(result));
            }
        }
        return checks;
    }

    public void deleteServiceCheck(long checkId) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement results = connection.prepareStatement(
                        "DELETE FROM service_check_results WHERE check_id = ?")) {
                    results.setLong(1, checkId);
                    results.executeUpdate();
                }
                try (PreparedStatement checks = connection.prepareStatement(
                        "DELETE FROM service_checks WHERE id = ?")) {
                    checks.setLong(1, checkId);
                    checks.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void recordServiceCheckResult(
            long checkId, long checkedAt, boolean success, long durationMillis)
            throws SQLException {
        String sql =
                "INSERT INTO service_check_results(check_id, checked_at, success, duration_millis) "
                        + "VALUES (?, ?, ?, ?)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, checkId);
            statement.setLong(2, checkedAt);
            statement.setInt(3, success ? 1 : 0);
            statement.setLong(4, durationMillis);
            statement.executeUpdate();
        }
    }

    public double serviceCheckAvailability(long checkId, long windowMillis) throws SQLException {
        String sql =
                """
                SELECT COUNT(*) AS total, SUM(success) AS success
                FROM service_check_results
                WHERE check_id = ? AND checked_at >= ?
                """;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, checkId);
            statement.setLong(2, System.currentTimeMillis() - windowMillis);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                long total = result.getLong("total");
                if (total == 0) {
                    return 1.0;
                }
                return result.getDouble("success") / total;
            }
        }
    }

    public AlertRecord resolveActiveAlert(String deviceId, AlertRuleType ruleType, long now)
            throws SQLException {
        try (Connection connection = open()) {
            Optional<AlertRecord> active = findActiveAlert(connection, deviceId, ruleType);
            if (active.isEmpty()) {
                return null;
            }
            return resolveAlert(connection, active.get().alertId(), now);
        }
    }

    private static Optional<ServiceCheck> findServiceCheckByName(Connection connection, String name)
            throws SQLException {
        String sql =
                "SELECT id, name, host, port, interval_millis, fail_threshold, enabled, created_at "
                        + "FROM service_checks WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(mapServiceCheck(result))
                        : Optional.empty();
            }
        }
    }

    private static ServiceCheck mapServiceCheck(ResultSet result) throws SQLException {
        return new ServiceCheck(
                result.getLong("id"),
                result.getString("name"),
                result.getString("host"),
                result.getInt("port"),
                result.getInt("interval_millis"),
                result.getInt("fail_threshold"),
                result.getInt("enabled") == 1,
                result.getLong("created_at"));
    }

    public List<AlertRecord> queryAlerts(
            Optional<String> deviceId,
            Optional<AlertRuleType> ruleType,
            Optional<AlertStatus> status,
            long fromMillis,
            long toMillis,
            int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, device_id, rule_type, severity, status, trigger_value, "
                        + "triggered_at, resolved_at, acknowledged_at, acknowledged_by, ack_comment "
                        + "FROM alerts WHERE triggered_at BETWEEN ? AND ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(fromMillis);
        parameters.add(toMillis);

        deviceId.ifPresent(value -> {
            sql.append(" AND device_id = ?");
            parameters.add(value);
        });
        ruleType.ifPresent(value -> {
            sql.append(" AND rule_type = ?");
            parameters.add(value.name());
        });
        status.ifPresent(value -> {
            sql.append(" AND status = ?");
            parameters.add(value.name());
        });
        sql.append(" ORDER BY triggered_at DESC, id DESC LIMIT ?");
        parameters.add(limit);

        List<AlertRecord> alerts = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    alerts.add(mapAlert(result));
                }
            }
        }
        return alerts;
    }

    public DemoCredentials seedDemoData(
            int deviceCount,
            String operatorUsername,
            char[] operatorPassword,
            Path configurationFile,
            String serverHost,
            int serverPort)
            throws SQLException, IOException {
        if (deviceCount < 1 || deviceCount > 100) {
            throw new IllegalArgumentException("deviceCount must be between 1 and 100");
        }
        String username = operatorUsername == null || operatorUsername.isBlank()
                ? "monitor"
                : operatorUsername.trim();
        char[] password = operatorPassword == null || operatorPassword.length == 0
                ? randomSecret()
                : operatorPassword.clone();
        String operatorPasswordText = new String(password);

        List<DemoCredentials.DeviceSecret> deviceSecrets = new ArrayList<>();
        long now = System.currentTimeMillis();
        boolean committed = false;
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                if (countRows(connection, "devices") > 0
                        || countRows(connection, "operators") > 0) {
                    throw new IllegalStateException(
                            "Database already contains accounts; seed demo data into a fresh database");
                }

                insertOperator(connection, username, passwordHasher.hash(password), now);
                for (int i = 1; i <= deviceCount; i++) {
                    String deviceId = "DEV-%03d".formatted(i);
                    String location = switch (i % 3) {
                        case 1 -> "机房 A 区";
                        case 2 -> "机房 B 区";
                        default -> "教学实验室";
                    };
                    char[] credential = randomSecret();
                    try {
                        insertDevice(
                                connection,
                                deviceId,
                                "设备 " + i,
                                location,
                                passwordHasher.hash(credential),
                                now);
                        deviceSecrets.add(new DemoCredentials.DeviceSecret(
                                deviceId, new String(credential)));
                    } finally {
                        java.util.Arrays.fill(credential, '\0');
                    }
                }
                connection.commit();
                committed = true;
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        if (!committed) {
            throw new IllegalStateException("Demo data transaction did not commit");
        }

        DemoCredentials credentials = new DemoCredentials(
                username, operatorPasswordText, List.copyOf(deviceSecrets), configurationFile);
        if (configurationFile != null) {
            writeDemoConfiguration(credentials, configurationFile, serverHost, serverPort);
        }
        return credentials;
    }

    private static int countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void insertOperator(
            Connection connection, String username, String passwordHash, long createdAt)
            throws SQLException {
        String sql =
                "INSERT INTO operators(username, password_hash, enabled, created_at) "
                        + "VALUES (?, ?, 1, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setLong(3, createdAt);
            statement.executeUpdate();
        }
    }

    private static void insertDevice(
            Connection connection,
            String deviceId,
            String displayName,
            String location,
            String credentialHash,
            long createdAt)
            throws SQLException {
        String sql =
                "INSERT INTO devices("
                        + "device_id, display_name, location, credential_hash, enabled, created_at"
                        + ") VALUES (?, ?, ?, ?, 1, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setString(2, displayName);
            statement.setString(3, location);
            statement.setString(4, credentialHash);
            statement.setLong(5, createdAt);
            statement.executeUpdate();
        }
    }

    private static void insertDeviceRules(
            Connection connection, String deviceId, DeviceRules rules, long updatedAt)
            throws SQLException {
        String sql =
                """
                INSERT INTO device_rules(
                    device_id, cpu_trigger, cpu_recover, cpu_samples, memory_trigger,
                    memory_recover, memory_samples, temp_trigger, temp_recover, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setDouble(2, rules.cpuTrigger());
            statement.setDouble(3, rules.cpuRecover());
            statement.setInt(4, rules.cpuRequiredSamples());
            statement.setDouble(5, rules.memoryTrigger());
            statement.setDouble(6, rules.memoryRecover());
            statement.setInt(7, rules.memoryRequiredSamples());
            statement.setDouble(8, rules.temperatureTrigger());
            statement.setDouble(9, rules.temperatureRecover());
            statement.setLong(10, updatedAt);
            statement.executeUpdate();
        }
    }

    private char[] randomSecret() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return encoded.toCharArray();
    }

    private static void writeDemoConfiguration(
            DemoCredentials credentials, Path file, String host, int port) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.setProperty("server.host", host);
        properties.setProperty("server.port", Integer.toString(port));
        properties.setProperty("monitor.username", credentials.operatorUsername());
        properties.setProperty("monitor.password", credentials.operatorPassword());
        properties.setProperty("device.count", Integer.toString(credentials.devices().size()));
        for (int i = 0; i < credentials.devices().size(); i++) {
            DemoCredentials.DeviceSecret device = credentials.devices().get(i);
            properties.setProperty("device." + i + ".id", device.deviceId());
            properties.setProperty("device." + i + ".credential", device.credential());
        }
        try (OutputStream output = Files.newOutputStream(absolute)) {
            properties.store(output, "Generated demo credentials - do not commit");
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private static void migrateLegacySchema(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "devices", "location",
                "ALTER TABLE devices ADD COLUMN location TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "telemetry", "disk_percent",
                "ALTER TABLE telemetry ADD COLUMN disk_percent REAL");
        rebuildAlertsIfLegacy(connection);
    }

    private static void addColumnIfMissing(
            Connection connection, String table, String column, String alterSql)
            throws SQLException {
        boolean found = false;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(alterSql);
            }
        }
    }

    private static void rebuildAlertsIfLegacy(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(alerts)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        String tableSql = "";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'alerts'")) {
            if (result.next()) {
                tableSql = result.getString("sql") == null ? "" : result.getString("sql");
            }
        }
        boolean hasNewColumns = columns.containsAll(
                Set.of("severity", "acknowledged_by", "ack_comment", "acknowledged_at"));
        boolean hasMemoryRule = tableSql.contains("'MEMORY'");
        boolean hasServiceDownRule = tableSql.contains("'SERVICE_DOWN'");
        boolean hasForeignKey = tableSql.contains("FOREIGN KEY");
        if (hasNewColumns && hasMemoryRule && hasServiceDownRule && !hasForeignKey) {
            return;
        }
        String acknowledgedAtSelect = columns.contains("acknowledged_at")
                ? "acknowledged_at"
                : "NULL";

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE alerts RENAME TO alerts_legacy");
            statement.execute(
                    """
                    CREATE TABLE alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        device_id TEXT NOT NULL,
                        rule_type TEXT NOT NULL
                            CHECK (rule_type IN ('CPU', 'MEMORY', 'TEMPERATURE', 'OFFLINE', 'SERVICE_DOWN')),
                        severity TEXT NOT NULL DEFAULT 'WARNING'
                            CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
                        status TEXT NOT NULL
                            CHECK (status IN ('ACTIVE', 'RESOLVED')),
                        trigger_value REAL,
                        triggered_at INTEGER NOT NULL,
                        resolved_at INTEGER,
                        acknowledged_at INTEGER,
                        acknowledged_by TEXT,
                        ack_comment TEXT
                    )
                    """);
            statement.execute(
                    "INSERT INTO alerts("
                            + "id, device_id, rule_type, severity, status, trigger_value, "
                            + "triggered_at, resolved_at, acknowledged_at, acknowledged_by, ack_comment"
                            + ") "
                            + "SELECT id, device_id, rule_type, 'WARNING', status, trigger_value, "
                            + "triggered_at, resolved_at, " + acknowledgedAtSelect + ", NULL, NULL "
                            + "FROM alerts_legacy");
            statement.execute("DROP TABLE alerts_legacy");
            statement.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_alerts_one_active_rule
                    ON alerts(device_id, rule_type)
                    WHERE status = 'ACTIVE'
                    """);
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_alerts_device_time "
                            + "ON alerts(device_id, triggered_at)");
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_alerts_status_time "
                            + "ON alerts(status, triggered_at)");
        }
    }

    private static AlertRecord findAlertById(Connection connection, long alertId)
            throws SQLException {
        String sql =
                """
                SELECT id, device_id, rule_type, severity, status, trigger_value,
                       triggered_at, resolved_at, acknowledged_at, acknowledged_by, ack_comment
                FROM alerts WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, alertId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Alert not found: " + alertId);
                }
                return mapAlert(result);
            }
        }
    }

    private static TelemetrySample mapTelemetry(ResultSet result) throws SQLException {
        double disk = result.getDouble("disk_percent");
        Double diskPercent = result.wasNull() ? null : disk;
        return new TelemetrySample(
                result.getString("device_id"),
                result.getLong("collected_at"),
                result.getLong("received_at"),
                result.getDouble("cpu_percent"),
                result.getDouble("memory_percent"),
                result.getDouble("temperature_celsius"),
                diskPercent);
    }

    private static AlertRecord mapAlert(ResultSet result) throws SQLException {
        double trigger = result.getDouble("trigger_value");
        Double triggerValue = result.wasNull() ? null : trigger;
        long resolved = result.getLong("resolved_at");
        Long resolvedAt = result.wasNull() ? null : resolved;
        long acknowledged = result.getLong("acknowledged_at");
        Long acknowledgedAt = result.wasNull() ? null : acknowledged;
        return new AlertRecord(
                result.getLong("id"),
                result.getString("device_id"),
                AlertRuleType.valueOf(result.getString("rule_type")),
                AlertSeverity.valueOf(result.getString("severity")),
                AlertStatus.valueOf(result.getString("status")),
                triggerValue,
                result.getLong("triggered_at"),
                resolvedAt,
                acknowledgedAt,
                result.getString("acknowledged_by"),
                result.getString("ack_comment"));
    }
}

