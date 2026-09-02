ALTER TABLE payment_alert_log
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER message,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN resolved_at DATETIME(6) NULL AFTER retry_count;

ALTER TABLE payment_alert_log
    ADD CONSTRAINT ck_payment_alert_log_status CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'FAILED'));
