ALTER TABLE settlement
    ADD CONSTRAINT uk_settlement_payment_id UNIQUE (payment_id);

CREATE INDEX idx_payment_status_id ON payment (status, id);
