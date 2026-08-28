CREATE TABLE payment_status_history
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payment_status_history_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT ck_payment_status_history_status CHECK (status IN
        ('READY', 'IN_PROGRESS', 'DONE', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'TIMEOUT_UNKNOWN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_status_history_payment_id_id
    ON payment_status_history (payment_id, id);
