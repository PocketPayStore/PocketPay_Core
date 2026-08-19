CREATE TABLE member
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT ck_member_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_id  BIGINT       NOT NULL,
    name       VARCHAR(200) NOT NULL,
    price      BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_product_seller_id ON product (seller_id);

CREATE TABLE stock
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id         BIGINT      NOT NULL,
    total_quantity     INT         NOT NULL,
    reserved_quantity  INT         NOT NULL DEFAULT 0,
    sold_quantity      INT         NOT NULL DEFAULT 0,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_stock_product_id UNIQUE (product_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE orders
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number    VARCHAR(50)  NOT NULL,
    member_id       BIGINT       NOT NULL,
    total_amount    BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('CREATED', 'STOCK_RESERVED', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_orders_member_id ON orders (member_id);

CREATE TABLE order_item
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    unit_price BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
CREATE INDEX idx_order_item_product_id ON order_item (product_id);

CREATE TABLE point_balance
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT      NOT NULL,
    balance    BIGINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_point_balance_member_id UNIQUE (member_id),
    CONSTRAINT fk_point_balance_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE point_ledger
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT      NOT NULL,
    order_id      BIGINT,
    type          VARCHAR(20) NOT NULL,
    amount        BIGINT      NOT NULL,
    balance_after BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_point_ledger_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_point_ledger_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_point_ledger_type CHECK (type IN ('EARN', 'USE', 'CANCEL_RESTORE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_point_ledger_member_id ON point_ledger (member_id);

CREATE TABLE payment
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id           BIGINT       NOT NULL,
    payment_method     VARCHAR(20)  NOT NULL,
    pg_provider        VARCHAR(50)  NOT NULL,
    pg_transaction_id  VARCHAR(100),
    idempotency_key    VARCHAR(100) NOT NULL,
    amount             BIGINT       NOT NULL,
    refundable_amount  BIGINT       NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL,
    approved_at        DATETIME(6),
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payment_status CHECK (status IN
        ('READY', 'IN_PROGRESS', 'DONE', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'TIMEOUT_UNKNOWN')),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CARD'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_order_id ON payment (order_id);

CREATE TABLE refund_request
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT       NOT NULL,
    request_amount  BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    requested_at    DATETIME(6)  NOT NULL,
    processed_at    DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_refund_request_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_refund_request_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT ck_refund_request_status CHECK (status IN
        ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'REJECTED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_refund_request_payment_id ON refund_request (payment_id);

CREATE TABLE payment_cancel
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id         BIGINT      NOT NULL,
    refund_request_id  BIGINT,
    cancel_amount      BIGINT      NOT NULL,
    reason             VARCHAR(200),
    canceled_at        DATETIME(6) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payment_cancel_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_payment_cancel_refund_request FOREIGN KEY (refund_request_id) REFERENCES refund_request (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_cancel_payment_id ON payment_cancel (payment_id);

CREATE TABLE settlement
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id          BIGINT      NOT NULL,
    seller_id           BIGINT      NOT NULL,
    amount              BIGINT      NOT NULL,
    pg_fee_amount       BIGINT      NOT NULL,
    platform_fee_amount BIGINT      NOT NULL,
    net_amount          BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settled_at          DATETIME(6),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    is_deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_settlement_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_settlement_seller FOREIGN KEY (seller_id) REFERENCES member (id),
    CONSTRAINT ck_settlement_status CHECK (status IN ('PENDING', 'SETTLED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_settlement_payment_id ON settlement (payment_id);
CREATE INDEX idx_settlement_seller_id ON settlement (seller_id);

CREATE TABLE saga_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT      NOT NULL,
    step          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_saga_log_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_saga_log_step CHECK (step IN ('PAYMENT', 'POINT_EARN', 'STOCK_CONFIRM', 'NOTIFICATION', 'SETTLEMENT')),
    CONSTRAINT ck_saga_log_status CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED', 'COMPENSATING', 'COMPENSATED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_saga_log_order_id ON saga_log (order_id);

CREATE TABLE outbox_event
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    retry_count    INT          NOT NULL DEFAULT 0,
    published_at   DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_event_aggregate_type CHECK (aggregate_type IN ('ORDER', 'PAYMENT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE pg_callback_log
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    pg_transaction_id VARCHAR(100),
    payload           JSON        NOT NULL,
    signature_valid   BOOLEAN     NOT NULL,
    processed         BOOLEAN     NOT NULL DEFAULT FALSE,
    retry_count       INT         NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_pg_callback_log_pg_transaction_id ON pg_callback_log (pg_transaction_id);
