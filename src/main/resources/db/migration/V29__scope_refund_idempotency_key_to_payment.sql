ALTER TABLE refund
    DROP INDEX uk_refund_idempotency_key,
    ADD CONSTRAINT uk_refund_payment_idempotency_key UNIQUE (payment_id, idempotency_key);
