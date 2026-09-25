ALTER TABLE orders
    DROP INDEX uk_orders_idempotency_key,
    ADD CONSTRAINT uk_orders_member_idempotency_key UNIQUE (member_id, idempotency_key);
