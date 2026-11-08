CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    location TEXT NOT NULL DEFAULT '',
    credential_hash TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS operators (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS telemetry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    collected_at INTEGER NOT NULL,
    received_at INTEGER NOT NULL,
    cpu_percent REAL NOT NULL CHECK (cpu_percent BETWEEN 0 AND 100),
    memory_percent REAL NOT NULL CHECK (memory_percent BETWEEN 0 AND 100),
    temperature_celsius REAL NOT NULL
        CHECK (temperature_celsius BETWEEN -40 AND 125),
    disk_percent REAL
        CHECK (disk_percent BETWEEN 0 AND 100),
    FOREIGN KEY (device_id) REFERENCES devices(device_id)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_device_time
    ON telemetry(device_id, received_at);

CREATE TABLE IF NOT EXISTS alerts (
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
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_alerts_one_active_rule
    ON alerts(device_id, rule_type)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_alerts_device_time
    ON alerts(device_id, triggered_at);

CREATE INDEX IF NOT EXISTS idx_alerts_status_time
    ON alerts(status, triggered_at);

CREATE TABLE IF NOT EXISTS device_rules (
    device_id TEXT PRIMARY KEY,
    cpu_trigger REAL NOT NULL,
    cpu_recover REAL NOT NULL,
    cpu_samples INTEGER NOT NULL,
    memory_trigger REAL NOT NULL,
    memory_recover REAL NOT NULL,
    memory_samples INTEGER NOT NULL,
    temp_trigger REAL NOT NULL,
    temp_recover REAL NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (device_id) REFERENCES devices(device_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target TEXT,
    detail TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS service_checks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    host TEXT NOT NULL,
    port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
    interval_millis INTEGER NOT NULL,
    fail_threshold INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS service_check_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    check_id INTEGER NOT NULL,
    checked_at INTEGER NOT NULL,
    success INTEGER NOT NULL CHECK (success IN (0, 1)),
    duration_millis INTEGER NOT NULL,
    FOREIGN KEY (check_id) REFERENCES service_checks(id)
);

CREATE INDEX IF NOT EXISTS idx_service_results_check_time
    ON service_check_results(check_id, checked_at);

CREATE INDEX IF NOT EXISTS idx_audit_log_time
    ON audit_log(created_at);

INSERT OR IGNORE INTO schema_version(version, applied_at)
VALUES (4, CAST(strftime('%s', 'now') AS INTEGER) * 1000);

