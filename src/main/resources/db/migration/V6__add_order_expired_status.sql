ALTER TABLE orders
    DROP CHECK ck_orders_status,
    ADD CONSTRAINT ck_orders_status CHECK (status IN
        ('CREATED', 'STOCK_RESERVED', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'EXPIRED'));

CREATE INDEX idx_orders_status_updated_at ON orders (status, updated_at);
