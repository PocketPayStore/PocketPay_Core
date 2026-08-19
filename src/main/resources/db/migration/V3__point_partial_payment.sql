ALTER TABLE payment
    ADD COLUMN used_point_amount BIGINT NOT NULL DEFAULT 0 AFTER amount;

ALTER TABLE saga_log
    DROP CHECK ck_saga_log_step;

ALTER TABLE saga_log
    ADD CONSTRAINT ck_saga_log_step CHECK (step IN
        ('PAYMENT', 'POINT_USE', 'POINT_EARN', 'STOCK_CONFIRM', 'NOTIFICATION', 'SETTLEMENT'));
