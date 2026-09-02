ALTER TABLE point_balance
    ADD COLUMN reserved_amount BIGINT NOT NULL DEFAULT 0 AFTER balance;

CREATE TABLE point_reservation
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    amount     BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_point_reservation_payment UNIQUE (payment_id),
    CONSTRAINT fk_point_reservation_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_point_reservation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_point_reservation_status CHECK (status IN ('RESERVED', 'USED', 'RELEASED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_point_reservation_member_id ON point_reservation (member_id);
