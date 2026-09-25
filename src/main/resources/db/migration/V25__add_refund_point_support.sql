ALTER TABLE payment
    ADD COLUMN refundable_point_amount BIGINT NOT NULL DEFAULT 0 AFTER refundable_amount;

ALTER TABLE point_earn_log
    ADD COLUMN reversed_amount BIGINT NOT NULL DEFAULT 0 AFTER amount;

CREATE INDEX idx_point_earn_log_payment_id ON point_earn_log (payment_id);
