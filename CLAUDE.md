# CLAUDE.md — payment-core

`payment-core`는 포켓몬 카드 스토어의 결제 코어 서버다. 스토어가 직접 재고를 파는 게 아니라, 회원이 자기 카드를 등록하면 다른 회원이 사가는 **위탁판매(마켓플레이스)** 구조다. 고객 화면은 없고, `/api/**` REST API만 제공한다. 이 문서는 이 레포를 개발할 때 필요한 내용만 담는다.

---

## 기술 스택

실제 `build.gradle` 기준 (그룹: `PocketPayStore`, 아티팩트: `PocketPay_Core`).

| 영역 | 기술 |
|---|---|
| Language | Java 17 (toolchain 고정) |
| Framework | **Spring Boot 4.1.0**, `spring-boot-starter-webmvc`(Boot 4부터 `-web`이 아니라 `-webmvc`로 이름 변경됨, 헷갈리지 말 것), Spring Data JPA |
| 외부 API 호출 | **Spring Cloud OpenFeign** (`spring-cloud-starter-openfeign`, Spring Cloud `2025.1.2`) — PG 호출용 `PgClient`는 `@FeignClient`로 구현 |
| Validation | `spring-boot-starter-validation` — 요청 DTO 검증(`@Valid`, `@NotNull` 등)에 사용 |
| Actuator | `spring-boot-starter-actuator` — 헬스체크, 추후 Prometheus 메트릭 노출용 |
| DB | MySQL(`mysql-connector-j`, runtime) + Flyway(`org.flywaydb:flyway-mysql`, `spring-boot-starter-flyway`) |
| 테스트 DB | H2 (`runtimeOnly`), `spring-boot-h2console`로 로컬에서 H2 콘솔 확인 가능 |
| Cache | `spring-boot-starter-data-redis` (캐싱 + Redisson 분산락) — 락/캐싱 단계부터 사용 |
| MQ | RabbitMQ — 비동기 전환 단계부터 사용, 그 전까지는 의존성도 추가하지 않는다 |
| 장애 대응 | Resilience4j (CircuitBreaker/Retry/TimeLimiter) — 캐싱/서킷브레이커 단계부터 사용, 별도 의존성 추가 필요 |
| 인증 | JWT **Access Token만** 우선 도입 (Refresh Token, Spring Security 풀스택은 아직 X). 인가 구분은 `member.role`(`USER`/`ADMIN`)로 한다 |
| 코드 생성 | Lombok (`compileOnly`/`annotationProcessor`) |
| 개발 편의 | `spring-boot-devtools` (developmentOnly) |
| 테스트 | JUnit 5 (`junit-platform-launcher`), Boot 4의 테스트 스타터 세트(`-actuator-test`, `-data-jpa-test`, `-data-redis-test`, `-flyway-test`, `-validation-test`, `-webmvc-test`)를 상황에 맞게 사용 |
| 부하테스트 | k6 |
| 배포 | Docker, AWS ECS Fargate |

**Spring Boot 4.x 관련 주의사항**
- 스타터 아티팩트명이 3.x와 다르다: `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**. 코드/문서에서 옛날 3.x 기준 아티팩트명을 쓰지 않도록 주의.
- Spring Cloud 버전은 `2025.1.2`로 Boot 4.1과 호환되는 트레인을 맞춰서 쓴다. Feign 관련 코드 작성 시 이 버전 기준 API를 따른다.
- Flyway는 `flyway-core`를 직접 넣지 않고 `spring-boot-starter-flyway` + `flyway-mysql`을 쓴다.

### 외부 PG 호출 — Feign Client로 구현

`PgClient`는 순수 인터페이스가 아니라 **`@FeignClient`** 기반으로 구현한다.

```java
@FeignClient(name = "mock-pg", url = "${mock-pg.base-url}")
public interface PgClient {

    @PostMapping("/mock-pg/approve")
    ApprovalResponse approve(@RequestBody ApprovalRequest request);

    @PostMapping("/mock-pg/cancel")
    CancelResponse cancel(@RequestBody CancelRequest request);

    @GetMapping("/mock-pg/transactions/{txId}")
    TransactionStatusResponse getTransaction(@PathVariable String txId);
}
```

- `mock-pg.base-url`은 프로필별(local/dev/prod)로 설정 분리
- 서킷브레이커/재시도(Resilience4j) 단계에서는 이 Feign Client 호출부에 `@CircuitBreaker`, `@Retry` 애노테이션을 붙이는 방식으로 적용한다 (Feign + Resilience4j 조합)
- 타임아웃 설정은 Feign 클라이언트 옵션(`Request.Options`)으로 명시적으로 짧게 잡아둔다 — 기본 타임아웃을 그대로 쓰면 장애 주입 실험(캐싱/서킷브레이커 단계)에서 의미 있는 지연을 재현하기 어렵다.

### DB 접근 전략

- 쓰기 경로(주문/결제/재고/포인트 — 락·Saga·Outbox 관여): **Spring Data JPA**. 현재는 비관적 락(`SELECT ... FOR UPDATE`)을 쓰고, 낙관적 락(`@Version`)은 단계 5(락 전략 실험)에서 다시 도입한다.
- 조회·집계 경로: **QueryDSL** (별도 의존성 추가 필요, 현재 build.gradle엔 없음 — 조회 로직 붙일 때 추가할 것).
- MyBatis는 쓰지 않는다.

---

## 도메인 규칙 (항상 지킬 것)

1. **금액은 `Long`(원 단위 정수) 또는 `BigDecimal`만 사용한다. `Double`/`Float` 금지.**
2. **모든 상태 변경은 상태 머신을 강제한다.** 불가능한 상태 전이(예: `CANCELED` → `DONE`)는 서비스 레이어에서 명시적으로 막는다.
3. **주문 생성/결제 승인 API는 `Idempotency-Key` 헤더 없이 처리하지 않는다.** Redis `SETNX` + DB `unique constraint`로 이중 방어한다.
4. **가용 재고 계산(`total_quantity - reserved_quantity - sold_quantity`)은 도메인 서비스 한 곳에만 구현한다.** 여러 곳에 중복 구현하지 않는다.
5. **PG 승인처럼 외부 호출은 트랜잭션 밖에서 동기로 수행한다.** DB 트랜잭션이 PG 상태까지 롤백해주지 않으므로, PG 승인 이후 단계가 실패하면 명시적으로 PG 취소 API를 호출하는 보상 로직을 둔다.
6. **포인트 잔액은 `point_balance`에서만 갱신하고, 모든 변동은 `point_ledger`에 append-only로 남긴다.**
7. **한 주문(order)에는 한 판매자(seller)의 상품만 담을 수 있다.** 서로 다른 판매자의 상품을 주문 생성 시 함께 담지 못하게 막는다 — `settlement`이 결제 1건당 1건으로 끝나는 전제가 여기서 나온다.

---

## ERD

### 회원/포인트

**member**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| email | VARCHAR(255) UNIQUE | |
| password | VARCHAR(255) | 평문 저장 (별도 암호화 없음) |
| name | VARCHAR(100) | |
| role | ENUM | USER / ADMIN — JWT Access Token 인가 구분용 |
| created_at | DATETIME | |

**point_balance**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| member_id | BIGINT UNIQUE, FK→member | member와 1:1 |
| balance | BIGINT | 원 단위, Double 금지 |
| updated_at | DATETIME | |

**point_ledger** (append-only)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| member_id | BIGINT FK | |
| order_id | BIGINT FK, nullable | |
| type | ENUM | EARN / USE / CANCEL_RESTORE |
| amount | BIGINT | 양수/음수 |
| balance_after | BIGINT | 스냅샷 |
| created_at | DATETIME | |

### 상품/재고

**product**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| seller_id | BIGINT FK→member | 카드를 등록한(파는) 회원. 위탁판매 구조라 필수 |
| name | VARCHAR(200) | 예: 리자몽 GX 부스터팩 |
| price | BIGINT | 원 단위 |
| created_at | DATETIME | |

> 판매 상품은 전부 한정판이라고 가정한다. `category`/`rarity`/`is_limited` 같은 상품 분류·한정판 여부 필드는 두지 않는다.
> 스토어가 직접 파는 재고는 없다 — 모든 상품은 회원이 등록한 것이다 (도메인 규칙 7번: 한 주문엔 한 판매자 상품만).

**stock** (락 전략 비교의 핵심 테이블)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| product_id | BIGINT UNIQUE, FK→product | product와 1:1 |
| total_quantity | INT | |
| reserved_quantity | INT | 주문 생성~결제 확정 사이 임시 예약 |
| sold_quantity | INT | 확정 판매 |
| updated_at | DATETIME | |

> 가용 재고 = total_quantity − reserved_quantity − sold_quantity. 주문 생성 시 reserved 증가, 결제 확정 시 reserved→sold, 실패/취소 시 reserved 원복.
> 현재는 비관적 락으로 동시성을 제어한다. `version`(낙관적 락)은 단계 5에서 다시 도입한다.

### 주문/결제

**orders**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_number | VARCHAR(50) UNIQUE | |
| member_id | BIGINT FK | |
| total_amount | BIGINT | |
| status | ENUM | CREATED / STOCK_RESERVED / PAYMENT_PENDING / PAID / FAILED / CANCELED / PARTIAL_CANCELED |
| idempotency_key | VARCHAR(100) UNIQUE | |
| created_at | DATETIME | |

**order_item**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| product_id | BIGINT FK | |
| quantity | INT | |
| unit_price | BIGINT | 주문 시점 가격 스냅샷 |

**payment**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| payment_method | ENUM | CARD (현재는 카드만 지원, 추후 확장) |
| pg_provider | VARCHAR(50) | |
| pg_transaction_id | VARCHAR(100) | |
| idempotency_key | VARCHAR(100) UNIQUE | |
| amount | BIGINT | |
| status | ENUM | READY / IN_PROGRESS / DONE / FAILED / CANCELED / PARTIAL_CANCELED / TIMEOUT_UNKNOWN |
| approved_at | DATETIME nullable | |
| created_at | DATETIME | |

**payment_cancel**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| payment_id | BIGINT FK | |
| cancel_amount | BIGINT | 부분취소 지원 |
| reason | VARCHAR(200) | |
| canceled_at | DATETIME | |

### 정산

**settlement**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| payment_id | BIGINT FK | |
| seller_id | BIGINT FK→member | 정산금을 지급받을 판매자 |
| amount | BIGINT | 정산 대상 금액(결제 금액) |
| pg_fee_amount | BIGINT | PG 수수료 |
| platform_fee_amount | BIGINT | 플랫폼(우리) 수수료 |
| net_amount | BIGINT | 판매자 실지급액 = amount − pg_fee_amount − platform_fee_amount |
| status | ENUM | PENDING / SETTLED / FAILED |
| settled_at | DATETIME nullable | 배치가 실제 판매자에게 지급 처리한 시각 (row 생성 시각인 created_at과 다름) |
| created_at | DATETIME | 사가의 "정산 데이터 적재" 단계에서 PENDING으로 생성되는 시각 |

> 실제 지급 처리(net_amount를 판매자 계좌로 송금)는 별도 배치 잡이 이 테이블을 읽어서 수행한다. 여기서는 데이터를 적재하는 부분까지만 다룬다. 한 주문은 한 판매자 상품만 담을 수 있어서(도메인 규칙 7번) payment 1건당 settlement 1건이다.

### Saga / Outbox

**saga_log**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| step | ENUM | PAYMENT / POINT_EARN / STOCK_CONFIRM / NOTIFICATION / SETTLEMENT |
| status | ENUM | STARTED / SUCCESS / FAILED / COMPENSATING / COMPENSATED |
| error_message | VARCHAR(500) nullable | |
| created_at | DATETIME | |

**outbox_event** (비동기 전환 단계부터 실제 사용, 그 전엔 테이블만 있고 코드에서 안 씀)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| aggregate_type | ENUM | ORDER / PAYMENT |
| aggregate_id | BIGINT | |
| event_type | VARCHAR(100) | |
| payload | JSON | |
| status | ENUM | PENDING / PUBLISHED / FAILED |
| retry_count | INT | |
| created_at | DATETIME | |
| published_at | DATETIME nullable | |

### PG 콜백

**pg_callback_log**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| pg_transaction_id | VARCHAR(100) | |
| payload | JSON | |
| signature_valid | BOOLEAN | |
| processed | BOOLEAN | |
| retry_count | INT | |
| received_at | DATETIME | |

> 서킷브레이커 상태(CLOSED/OPEN/HALF_OPEN)와 락 충돌 횟수는 별도 테이블 대신 Resilience4j/Micrometer가 노출하는 Prometheus 메트릭 + Grafana로 관찰한다. (`circuit_breaker_log`, `lock_conflict_log`는 두지 않기로 함)

### 관계 요약

```
member 1---1 point_balance
member 1---N point_ledger
member 1---N orders
orders 1---N order_item
orders 1---N payment
orders 1---N saga_log
payment 1---N payment_cancel
payment 1---N pg_callback_log (pg_transaction_id 매칭)
product 1---1 stock
product 1---N order_item
member 1---N product (판매자)
payment 1---N settlement
member 1---N settlement (판매자, 지급 대상)
```

---

## API

### 이 레포가 제공하는 API

상품/포인트 조회 같은 "화면용" API는 만들지 않는다. 테스트 회원(id 1~10, `test{id}@test.com`/`test{id}`)과 상품/재고는 Flyway 시드 데이터(`V2__seed_data.sql`, product id 1~10, 판매자는 회원 1~7에 분산, 재고는 1~500으로 다양하게)로 고정 ID를 심어두고 사용한다.

| Method | Endpoint | 설명 | 비고 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 (재고 임시 예약) | `Idempotency-Key` 헤더 필수 |
| POST | `/api/payments` | 결제 승인 요청 | `Idempotency-Key` 헤더 필수 |
| GET | `/api/payments/{id}` | 결제 상태 조회 | 망취소 재확인용 |
| POST | `/api/payments/{id}/cancel` | 결제 취소(전체/부분) | |
| POST | `/api/webhooks/pg` | PG 웹훅 수신 | HMAC 서명 검증 |


### 이 레포가 호출하는 외부 PG API (계약, `PgClient` Feign 인터페이스로 구현 — 기술스택 섹션 참고)

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/mock-pg/approve` | 승인 요청 |
| POST | `/mock-pg/cancel` | 취소 요청 |
| GET | `/mock-pg/transactions/{txId}` | 거래 상태 조회 — 망취소 재확인에 반드시 사용 |
| (수신) `POST /api/webhooks/pg` | PG가 승인 완료 후 보내는 콜백 |

---

## 개발 단계

**단계 1 — 설계/스캐폴딩**
- ERD 확정, Flyway 마이그레이션 작성
- 주문/결제 상태 머신 확정 (불가능한 전이 정의)
- 프로젝트 스캐폴딩, Docker Compose(MySQL + Redis만, RabbitMQ는 아직 X)

**단계 2 — 도메인 기본 구현**
- 상품/재고/회원/포인트 도메인
- 주문 생성 API (재고 임시 예약)
- 결제 승인 API + 외부 PG 동기 연동
- 멱등키 처리 (Redis SETNX + DB unique constraint)

**단계 3 — Saga (RabbitMQ/Outbox 없이 인프로세스로만, Before/After 스토리 없이 처음부터 완성 형태로 구현)**
```
결제 승인 요청
  → PG 승인 (PgClient, 트랜잭션 밖, 동기)
  → 결제 상태 저장 (@Transactional, DB 쓰기만)
  → [AFTER_COMMIT] ApplicationEventPublisher 발행 (같은 프로세스, 큐 없음)
      → 포인트 적립 리스너 (자기 트랜잭션)
      → 재고 확정 리스너 (자기 트랜잭션)
      → 알림 리스너 (트랜잭션 없음)
      → 정산 데이터 적재 리스너 (자기 트랜잭션)
  → 응답
```
- `@TransactionalEventListener(AFTER_COMMIT)` + `ApplicationEventPublisher`만 사용. RabbitMQ, Outbox 둘 다 이 단계에서 쓰지 않는다.
- 중간 실패 시 보상 트랜잭션 이벤트 발행 → 이전 단계 명시적 취소 (필요하면 PG 취소 API 호출까지 포함)
- 망취소 로직: 승인 타임아웃 시 `/mock-pg/transactions/{txId}`로 실제 상태 재확인
- **필수 통합테스트**: 동시 주문 100건 → 중복 승인 0건 / 포인트 적립 강제 실패 → 보상 트랜잭션으로 결제 취소·재고 원복 완료 검증 (CI 등록, 완화 금지)

**단계 4 — 비동기 전환 (여기서 처음 RabbitMQ + Outbox 도입)**
- 단계 3 구조 그대로 k6 baseline 측정 (TPS/p50/p95/p99/에러율)
- Outbox 테이블 + Relay 신규 도입 (DB 쓰기와 메시지 발행의 원자성을 위해 이 시점에 필요해짐)
- RabbitMQ 신규 도입, Outbox Relay → 큐 발행 → 포인트/재고/알림/정산 컨슈머 분리
- PG 웹훅 수신도 즉시 200 응답 후 큐 적재로 전환
- 재측정 후 Before/After 비교

**단계 5 — 락 전략 (Before = 비관적 락)**
```java
@Transactional
public void deductStock(Long productId, int qty) {
    Stock stock = stockRepository.findByIdForUpdate(productId);
    stock.deduct(qty);
}
```
- k6 시나리오 A(정상, 서로 다른 상품)/B(충돌, 동일 한정판 상품 집중) 분리 측정
- 개선: `@Version` 낙관적 락 + `@Retryable` 재시도, 전역 임계구역만 최소 범위로 Redisson 분산락 보완
- 락 충돌 횟수는 별도 테이블 없이 재시도/실패 메트릭(Micrometer)으로 관찰

**단계 6 — 캐싱 + 서킷브레이커**
- Before: PG/상품 설정 매 요청 DB 조회, PG 호출부 보호장치 없음
- 장애 주입(지연/에러) 시 스레드풀 고갈로 무관한 다른 주문까지 영향받는 것을 확인
- 개선: Redis 캐싱 + 캐시 무효화, Resilience4j CircuitBreaker/Retry/TimeLimiter를 PG 호출부에 적용
- Rate Limiting(토큰버킷). 서킷브레이커 상태 전이는 Resilience4j가 노출하는 Micrometer 메트릭 + Prometheus/Grafana로 관찰 (별도 테이블 없음)

---

## 성능 실험 원칙 (단계 4, 5, 6)

- 최소 3회 반복 측정 후 평균/중앙값 사용
- 테스트 환경(리소스 제한, 데이터량) 기록해 재현성 확보
- TPS, p50/p95/p99, 에러율 기본 기록
- "개선됐다"만 쓰지 말고 트레이드오프도 같이 기록

---

## 커맨드

```bash
./gradlew test                 # H2 기반 테스트 실행 (테스트 스타터: -actuator-test, -data-jpa-test, -data-redis-test, -flyway-test, -validation-test, -webmvc-test)
./gradlew bootRun              # 로컬 실행 (MySQL/Redis는 docker-compose로 먼저 기동)
./gradlew flywayMigrate        # DB 마이그레이션
```