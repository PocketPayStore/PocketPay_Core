ALTER TABLE refund_request RENAME TO refund;

ALTER TABLE refund RENAME INDEX uk_refund_request_idempotency_key TO uk_refund_idempotency_key;

ALTER TABLE refund
    DROP FOREIGN KEY fk_refund_request_payment,
    ADD CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment (id);

ALTER TABLE refund
    DROP CHECK ck_refund_request_status,
    ADD CONSTRAINT ck_refund_status CHECK (status IN
        ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'REJECTED'));

ALTER TABLE payment_cancel
    DROP FOREIGN KEY fk_payment_cancel_refund_request;

ALTER TABLE payment_cancel
    RENAME COLUMN refund_request_id TO refund_id;

ALTER TABLE payment_cancel
    ADD CONSTRAINT fk_payment_cancel_refund FOREIGN KEY (refund_id) REFERENCES refund (id);
