CREATE TABLE point_earn_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    order_id    BIGINT       NOT NULL,
    payment_id  BIGINT       NOT NULL,
    amount      BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    resolved_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_point_earn_log_status CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'FAILED'))
) ENGINE = InnoDB;

CREATE INDEX idx_point_earn_log_order_id ON point_earn_log (order_id);
CREATE INDEX idx_point_earn_log_status_id ON point_earn_log (status, id);
