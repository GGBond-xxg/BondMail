CREATE TABLE IF NOT EXISTS devices (
    installation_id TEXT PRIMARY KEY,
    installation_secret_hash TEXT NOT NULL,
    fcm_token TEXT NOT NULL UNIQUE,
    interval_minutes INTEGER NOT NULL CHECK (interval_minutes IN (1, 5, 10, 15, 30, 60)),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    next_sync_at INTEGER NOT NULL,
    last_sent_at INTEGER,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    app_version TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_devices_due
    ON devices (enabled, next_sync_at);
