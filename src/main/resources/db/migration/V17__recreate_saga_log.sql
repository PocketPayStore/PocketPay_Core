CREATE TABLE saga_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id    BIGINT      NOT NULL,
    order_id      BIGINT      NOT NULL,
    step          VARCHAR(30) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    attempts      INT         NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_saga_log_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT uk_saga_log_payment_step UNIQUE (payment_id, step),
    CONSTRAINT ck_saga_log_step CHECK (step IN ('POINT_EARN', 'STOCK_CONFIRMATION', 'NOTIFICATION')),
    CONSTRAINT ck_saga_log_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'COMPENSATING', 'COMPENSATED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_saga_log_step_status_id ON saga_log (step, status, id);
