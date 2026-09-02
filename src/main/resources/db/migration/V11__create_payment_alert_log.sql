CREATE TABLE payment_alert_log
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50)  NOT NULL,
    severity   VARCHAR(20)  NOT NULL,
    payment_id BIGINT       NULL,
    order_id   BIGINT       NULL,
    message    VARCHAR(500) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    INDEX idx_payment_alert_log_payment_id (payment_id),
    INDEX idx_payment_alert_log_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
