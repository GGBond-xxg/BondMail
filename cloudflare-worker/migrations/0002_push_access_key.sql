ALTER TABLE devices ADD COLUMN access_key_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_devices_authorized_due
    ON devices (access_key_hash, enabled, next_sync_at);
