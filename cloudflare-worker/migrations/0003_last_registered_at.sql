ALTER TABLE devices ADD COLUMN last_registered_at INTEGER;

-- Give every currently registered installation a full grace period after this migration. Future
-- registrations update this field independently from scheduled sends and delivery failures.
UPDATE devices
   SET last_registered_at = CAST(strftime('%s', 'now') AS INTEGER)
 WHERE last_registered_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_devices_last_registered
    ON devices (last_registered_at);
