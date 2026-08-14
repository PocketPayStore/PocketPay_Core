-- ============================================================================
-- V1: 초기 스키마
-- CLAUDE.md의 ERD를 기준으로 한다. 모든 테이블은 BaseEntity가 강제하는
-- created_at / updated_at / is_deleted 공통 컬럼을 가진다.
-- ============================================================================

-- ── 회원 ─────────────────────────────────────────────────────────────────────
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

-- ── 상품/재고 ─────────────────────────────────────────────────────────────────
-- 위탁판매 상품: 회원이 자기 카드를 등록해서 파는 구조라 seller_id가 필수다.
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

-- product와 1:1인 재고. PK는 별도 auto_increment id, product_id는 UNIQUE로 1:1을 보장한다.
-- 가용 재고 = total_quantity - reserved_quantity - sold_quantity (계산은 도메인 서비스에서).
CREATE TABLE stock
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id         BIGINT      NOT NULL,
    total_quantity     INT         NOT NULL,
    reserved_quantity  INT         NOT NULL DEFAULT 0,
    sold_quantity      INT         NOT NULL DEFAULT 0,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_stock_product_id UNIQUE (product_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── 주문 ─────────────────────────────────────────────────────────────────────
-- 테이블명은 "order"가 SQL 예약어라 "orders"를 쓴다.
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

-- ── 포인트 ────────────────────────────────────────────────────────────────────
-- member와 1:1인 잔액. PK는 별도 auto_increment id, member_id는 UNIQUE로 1:1을 보장한다.
-- 갱신은 이 테이블에서만 하고, 모든 변동은 point_ledger에 append-only로 남긴다.
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

-- ── 결제 ─────────────────────────────────────────────────────────────────────
CREATE TABLE payment
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT       NOT NULL,
    payment_method    VARCHAR(20)  NOT NULL,
    pg_provider       VARCHAR(50)  NOT NULL,
    pg_transaction_id VARCHAR(100),
    idempotency_key   VARCHAR(100) NOT NULL,
    amount            BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    approved_at       DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payment_status CHECK (status IN
        ('READY', 'IN_PROGRESS', 'DONE', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'TIMEOUT_UNKNOWN')),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CARD'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_order_id ON payment (order_id);

CREATE TABLE payment_cancel
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id    BIGINT      NOT NULL,
    cancel_amount BIGINT      NOT NULL,
    reason        VARCHAR(200),
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payment_cancel_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_cancel_payment_id ON payment_cancel (payment_id);

-- 판매자(seller_id)에게 지급할 정산금. 실제 지급 처리는 배치 잡이 하지만, 사가의
-- "정산 데이터 적재" 단계가 PENDING row를 만드는 테이블이라 여기 둔다.
-- net_amount = amount - pg_fee_amount - platform_fee_amount, 이 금액이 판매자에게 지급된다.
-- 한 주문은 한 판매자의 상품만 담을 수 있다는 전제라 payment 1건당 settlement 1건이다.
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

-- ── Saga / Outbox ────────────────────────────────────────────────────────────
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

-- 비동기 전환 단계(단계 4) 전까지는 테이블만 존재하고 코드에서는 쓰지 않는다.
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

-- ── PG 콜백 ───────────────────────────────────────────────────────────────────
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
