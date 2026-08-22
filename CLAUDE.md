# CLAUDE.md — payment-core

`payment-core`는 포켓몬 카드 스토어의 결제 코어 서버다. **스토어가 업체(카드 공급사)로부터 카드팩을 공급받아 특정 시간에 판매하고, 판매되면 그 대금이 업체에게 정산되는 구조**다. 회원은 전부 구매자다. 고객 화면은 없고, `/api/**` REST API만 제공한다. 이 문서는 이 레포를 개발할 때 필요한 내용만 담는다.

---

## 포트폴리오 5대 요소 — 각 항목의 성격(성능/안정성)을 명확히 구분한다

**서로 다른 두 요소가 애매하게 겹치지 않도록, 이 프로젝트는 항목마다 "성능을 재는 것"과 "정확성/안정성을 지키는 것"을 명확히 하나로 라벨링한다.** 코드를 작성할 때 자신이 지금 만드는 게 실측 비교 대상인지, 아니면 그냥 올바르게 지어야 하는 전제조건인지 항상 구분할 것.

| # | 요소 | 성격 | 범위 |
|---|---|---|---|
| 1 | 결제 코어 정합성 | **안정성**, Before/After 없음 | 결제 승인이라는 하나의 프로세스가 시간축을 따라 올바르게 완결되는가 (Saga, 멱등성, 상태 머신) |
| 2 | 비동기 처리 | **성능**, Before/After | 요소 1의 결과를 다른 시스템에 전파하는 속도 (동기→RabbitMQ+Outbox) |
| 3a | 재고 차감 동시성 | **성능**, Before/After | 비관적 락 단독(처음부터) → Redisson 분산락 단독(FOR UPDATE 제거), DB 커넥션 풀 점유·TPS·p95 비교 |
| 3c | 재고 선점-만료 경합 | **안정성**, Before/After 없음 | 처음부터 `orders` 단일 테이블 락으로 만료 배치 vs 결제 승인 경쟁 처리 |
| 4 | 서킷브레이커 | **안정성**, Before/After 없음 (Retry는 이미 있음, CircuitBreaker/TimeLimiter만 추가) | 장애 격리, PG 에러 분류 |
| 5 | 정산 배치 + 대사 | **안정성**, Before/After 없음 | PG 정산파일과 내부 데이터 사후 대조 |

**요소 3은 하위 항목(3a/3c)이 서로 다른 성격을 갖는다.** 3a(재고)는 실측 비교 실험이고, 3c(선점-만료)는 "왜 이 설계가 맞는지"를 증명하는 정합성 테스트다. 이 둘을 뭉뚱그려서 "동시성 제어" 하나로 설명하지 말고, 항상 어느 하위 항목을 얘기하는지 구분해서 코드/문서를 짤 것. 요소 4는 캐싱을 검토했으나 이 프로젝트 구조상 캐싱 대상(자주 안 바뀌면서 반복 조회되고 DB 부하가 유의미한 데이터)이 마땅치 않아 제외했고, 서킷브레이커 단일 항목으로 구성한다.

**요소 3a의 Before/After 축은 "비관적 락 단독 → Redisson 분산락 단독"이다.** 재고는 한정판 핫 아이템 특성상 동일 상품에 동시 주문이 몰려 충돌이 잦다. 그래서 낙관적 락 재시도가 아니라 처음부터 `SELECT ... FOR UPDATE` 비관적 락으로 즉시 직렬화하는 걸 **Before**로 잡는다(이건 3c·환불 처리와 같은 이유로 자연스러운 선택). 문제는 이 방식이 대기하는 동안 **DB 커넥션 풀(HikariCP)의 커넥션을 계속 붙들고 있다는 것** — 충돌이 심해지면 대기 트랜잭션들이 커넥션 풀을 소진시켜, 그 상품과 무관한 다른 요청까지 커넥션을 못 받아 지연되는 문제로 번진다(요소 4의 톰캣 스레드풀 고갈과 같은 메커니즘인데, 병목이 DB 커넥션 풀이라는 점이 다르다). **After**는 Redisson 분산락으로 같은 상품 키(`lock:stock:{productId}`)에 대한 대기를 Redis 쪽으로 옮기고, `SELECT ... FOR UPDATE`는 아예 걷어낸다 — 락을 못 얻은 요청은 DB 트랜잭션을 열지 않고 Redis에서만 대기하므로(`RLock`은 pub/sub 기반이라 폴링이 아니라 락 해제 이벤트로 깨어남) DB 커넥션을 점유하지 않고, 락을 얻은 요청만 짧게 DB 트랜잭션을 열어 곧장 갱신 후 커밋한다. FOR UPDATE를 같이 유지하는 안도 검토했으나, 재고를 건드리는 경로가 예약/확정/원복 셋으로 나뉘어 있어 그중 하나라도 Redisson 락 밖에 있으면 FOR UPDATE만으로는 못 막는 비대칭 lost-update가 생길 수 있는 반면, 셋 다 Redisson 락으로 감싸면 FOR UPDATE는 항상 비경합으로 즉시 풀리는 순수 오버헤드만 남는다 — 그래서 After는 Redisson 락 하나로 대체하고 FOR UPDATE는 제거하는 쪽을 택했다. 정상 트래픽(충돌 거의 없음)에서는 Redis 왕복이라는 추가 홉이 오히려 오버헤드가 될 수 있다는 트레이드오프도 반드시 같이 측정·서술한다.

이 `lock:stock:{productId}` 락은 재고를 예약하는 주문 생성(`reserve`)만이 아니라, 결제 승인 성공 시 reserved→sold로 확정하는 지점(`confirmForOrder`, 결제 사가 안에서 호출)과 결제 실패·만료로 reserved를 원복하는 지점(`releaseForOrder`, 보상 트랜잭션/만료 배치에서 호출)에도 동일하게 건다. 셋 다 결국 같은 `stock` 행을 읽고 쓰는데, Redisson 락은 애노테이션이 붙은 호출 지점 단위로만 걸리므로 한 경로라도 이 락 밖에 두면 그 경로가 그대로 동시성 구멍이 된다. **알려진 잔여 리스크**: `DistributedLockAop`는 `tryLock(waitTime, leaseTime, ...)`에 명시적 `leaseTime`을 넘겨 호출하므로 Redisson의 워치독(락 보유 스레드가 살아있는 동안 자동 갱신)이 붙지 않는다 — 임계구역 처리가 `lock.default-lease-time-seconds`(기본 3초)를 넘기면 락이 자동 해제되어 두 요청이 동시에 들어올 수 있고, FOR UPDATE 없이는 이 경우를 막을 방법이 없다. 지금은 임계구역이 단순 CRUD라 3초를 넘길 일이 거의 없다고 보고 감수하는 트레이드오프이며, 걱정되면 `leaseTime`을 넉넉히 늘리거나 워치독을 쓰도록 `DistributedLockAop`를 바꾸는 걸 후속 과제로 남긴다.

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
| Cache | `spring-boot-starter-data-redis` — 멱등키 SETNX/TTL 응답 캐싱(요소 1)과 Redisson 분산락(요소 3a — 비관적 락 단독과 짝을 이루는 성능 실험 대상, DB 커넥션 풀 점유를 Redis 대기로 옮기는 목적)에 사용. 조회 데이터 캐싱(상품/PG 설정 등)은 검토했으나 이 프로젝트에서 캐싱할 만큼 반복 조회되면서 자주 안 바뀌는 데이터가 마땅치 않아 제외 |
| MQ | RabbitMQ — 요소 2(비동기 처리)부터 사용, 그 전까지는 의존성도 추가하지 않는다 |
| 장애 대응 | Resilience4j. **Retry는 처음부터 도입되어 있음**(멱등키 덕분에 PG 승인 재시도가 안전해서) — `PgClient`에 `@Retry(name = "pgClient")`. **CircuitBreaker/TimeLimiter는 요소 4에서만 추가**한다 — Retry가 "있고 없고"가 아니라 "재시도를 다 써도 실패가 반복될 때 스레드를 계속 붙잡을지, 즉시 차단할지"가 요소 4의 실제 개선 포인트다. 라이브러리는 이미 있어서 애노테이션+설정만 추가하면 됨 |
| 인증 | JWT **Access Token만** 우선 도입 (Refresh Token, Spring Security 풀스택은 아직 X). 인가 구분은 `member.role`(`USER`/`ADMIN`)로 한다 |
| 코드 생성 | Lombok (`compileOnly`/`annotationProcessor`) |
| 개발 편의 | `spring-boot-devtools` (developmentOnly) |
| 테스트 | JUnit 5 (`junit-platform-launcher`), Boot 4의 테스트 스타터 세트(`-actuator-test`, `-data-jpa-test`, `-data-redis-test`, `-flyway-test`, `-validation-test`, `-webmvc-test`)를 상황에 맞게 사용 |
| 부하테스트 | k6 (성능형 항목: 요소 2, 3a에서만 사용. 안정성형 항목은 통합테스트/레이스 재현 테스트로 검증) |
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

    @Retry(name = "pgClient")
    @PostMapping("/mock-pg/approve")
    ApprovalResponse approve(@RequestBody ApprovalRequest request);

    @Retry(name = "pgClient")
    @PostMapping("/mock-pg/cancel")
    CancelResponse cancel(@RequestBody CancelRequest request);

    @Retry(name = "pgClient")
    @GetMapping("/mock-pg/transactions/{txId}")
    TransactionStatusResponse getTransaction(@PathVariable String txId);
}
```

- `mock-pg.base-url`은 프로필별(local/dev/prod)로 설정 분리 (dev는 mock-pg EC2 private IP)
- `@Retry`는 `approve`/`cancel`/`getTransaction` 전부에 적용했다. `approve` 요청엔 `merchantTransactionId`(우리 쪽 idempotencyKey)를 실어 보내므로, 재시도로 같은 요청이 두 번 가도 PG가 멱등키 기준으로 중복 승인을 막아준다는 전제라 재시도가 안전하다.
- 모든 예외를 재시도하진 않는다(`PgClientRetryConfig`, `RetryConfigCustomizer`로 `pgClient` 인스턴스에 커스텀 `retryOnException` predicate 적용): 네트워크 타임아웃/연결거부 같은 `IOException`, PG 서버 쪽 5xx는 일시적 장애일 수 있어 재시도 대상. 4xx(카드번호 오류·잔액 부족처럼 PG가 즉시 판단한 결정적 실패, **유저 귀책 에러**)는 재시도하지 않고 바로 실패로 처리한다. **이 "일시적 장애=재시도 / 결정적 실패=즉시 실패" 구분은 요소 4(서킷브레이커)의 실패율 집계 설계에도 그대로 이어진다** — 뒤에서 다시 설명.
- 재시도가 다 실패하면(=`PaymentService`의 catch 블록) `TIMEOUT_UNKNOWN`으로 상태만 남기고 응답한다. 요청 스레드에서 `getTransaction`을 동기로 호출해 재확인하지 않는다. 대신 `TIMEOUT_UNKNOWN` 상태의 `payment` 로우 자체가 "재확인이 필요하다"는 기록이고, 요소 3c(선점-만료 배치)가 이 상태를 반드시 먼저 재확인한 뒤에만 관련 주문을 만료 처리한다.
- **PG 승인 성공 직후 DB 커넥션이 끊기는 케이스**(유저 돈은 이미 빠졌는데 우리 시스템은 결제 사실을 모르는 최악의 상태)는 재확인 배치로도 못 잡으면 반드시 별도 창구(Slack 알림, DB 에러 테이블 등 이 레포 밖의 채널)에 남겨야 한다. 지금은 이 로깅이 미구현 상태.
- 타임아웃 설정은 Feign 클라이언트 옵션(`Request.Options`)으로 명시적으로 짧게 잡아둔다(connect 2s / read 3s) — 기본 타임아웃을 그대로 쓰면 요소 4의 장애 주입 실험에서 의미 있는 지연을 재현하기 어렵다.

### DB 접근 전략

- 쓰기 경로(주문/결제/재고/포인트 — 락·Saga·Outbox 관여): **Spring Data JPA**. `payment` 행(환불 처리, 도메인 규칙 11), `orders` 행(만료-승인 경합, 요소 3c)은 비관적 락(`SELECT ... FOR UPDATE`)을 처음부터 쓴다. `stock` 행(재고 차감, 요소 3a)은 Before(비관적 락 단독)에서 출발해 After에서는 FOR UPDATE를 걷어내고 Redisson 분산락(`lock:stock:{productId}`) 하나로 대체한다 — 재고 예약(주문 생성)·확정(결제 승인 성공)·원복(보상/만료)까지, `stock` 행을 건드리는 모든 지점이 이 락 하나만 거친다.
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
7. **한 주문(order)에는 한 상품(product) 종류만 담을 수 있다.** 수량은 자유롭게 담을 수 있지만, 서로 다른 상품(당연히 서로 다른 업체 상품도 포함)을 한 주문에 함께 담지 못하게 막는다 — 상품이 하나면 공급 업체도 자동으로 하나라, `settlement`이 결제 1건당 1건으로 끝나는 전제가 여기서 나온다.
8. **멱등키 응답은 TTL을 가진 캐시로 다룬다.** `Idempotency-Key`로 성공 응답을 만들면 Redis에 `{idempotencyKey → 응답}`을 TTL과 함께 저장하고, TTL 내 동일 키 재요청은 이 캐시된 응답을 그대로 반환한다. **TTL이 지난 뒤 동일 키가 다시 오면 성공 응답을 다시 주는 게 아니라 명시적인 "중복 승인 오류"로 처리한다.** (결제 승인 API(`/api/payments`)엔 구현됨 — `IdempotencyKeyGuard.executeIdempotent`가 락을 못 잡으면 즉시 거절하는 대신 처리 중인 요청이 끝날 때까지 대기했다가 캐시된 응답을 반환한다. 주문 생성/환불 요청 API는 처리 자체가 빠르고 재요청 시 같은 응답을 그대로 받아야 할 절박함이 적어서, 3번 규칙의 fail-fast SETNX(`tryAcquire`)만 쓴다.)
9. **여러 테이블에 동시에 비관적 락을 걸지 않는다.** 요소 3c(재고 선점-만료 경합)에서 만료 대상 거래(`orders`)에만 비관적 락을 걸고, 재고 등 유관 도메인 상태 변경은 별도 트랜잭션/이벤트로 비동기 처리하는 이유가 이거다 — 여러 테이블을 동시에 비관적 락으로 잡으면 데드락이 생길 수 있다.
10. **비동기 이벤트(요소 2 RabbitMQ 도입 이후)는 무조건 적용하지 않는다.** 컨슈머는 이벤트를 처리하기 전에 "지금 이걸 적용해도 되는 상태인가"를 먼저 검증한다. 순서가 안 맞으면(예: 결제완료보다 환불완료 이벤트가 먼저 소비되는 경우) 명시적으로 실패시켜 재처리 큐로 돌린다.
11. **환불 처리는 `payment` 행에 비관적 락을 걸어 순차 처리한다.** 환불 가능 잔여 금액(`payment.refundable_amount`)은 환불 처리 시 해당 행에 락을 걸고 직접 차감한다 — 환불은 충돌 자체가 드물지만, 실패보다 확실한 순차 처리가 더 중요해서 비관적 락을 선택했다. 재고 차감(요소 3a)도 Before 단계에서는 같은 이유로 비관적 락을 쓰지만(재고는 충돌이 잦아서, 환불은 순차 처리 보장이 중요해서) — 재고 쪽만 After에서 Redisson 분산락으로 교체된다는 점이 다르다. 환불은 이 축의 실험 대상이 아니라 계속 `payment` 행 비관적 락을 쓰고, 락 대상 테이블도 `stock` 행과 `payment` 행으로 서로 다르다.

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

**vendor** (카드팩을 공급하는 업체 — 정산 대상)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR(200) | 업체명 |
| created_at | DATETIME | |

> 최소 구성만 둔다. 실제 입금 계좌·사업자번호 같은 정산 실행 정보는 이 레포 범위 밖(정산 배치 담당자가 수동으로 처리)이고, 여기서는 정산 대상을 식별하는 용도로만 쓴다.

**product**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| vendor_id | BIGINT FK→vendor | 이 카드팩을 공급한 업체 |
| name | VARCHAR(200) | 예: 리자몽 GX 부스터팩 |
| price | BIGINT | 원 단위 |
| created_at | DATETIME | |

> 판매 상품은 전부 한정판이라고 가정한다. 스토어가 직접 재고를 보유·등록하지 않고, 업체로부터 공급받은 카드팩을 판매한다(도메인 규칙 7번). 판매 시작 시각(드롭/한정 판매 타이밍) 개념은 아직 스키마에 반영하지 않았다.

**stock** (요소 3a — 재고 동시성 실험의 핵심 테이블)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| product_id | BIGINT UNIQUE, FK→product | product와 1:1 |
| total_quantity | INT | |
| reserved_quantity | INT | 주문 생성~결제 확정 사이 임시 예약 |
| sold_quantity | INT | 확정 판매 |
| updated_at | DATETIME | |

> 가용 재고 = total_quantity − reserved_quantity − sold_quantity. 주문 생성 시 reserved 증가, 결제 확정 시 reserved→sold, 실패/취소/만료 시 reserved 원복.
> 재고 차감은 비관적 락(`SELECT ... FOR UPDATE`)으로 구현한다(요소 3a의 Before, `@Version` 컬럼은 쓰지 않음). 요소 3a의 실험 축은 "비관적 락 단독 vs Redisson 분산락 단독"이다. After는 FOR UPDATE를 걷어내고 예약(`reserve`)·확정(`confirmForOrder`)·원복(`releaseForOrder`) 세 지점 모두에 같은 `lock:stock:{productId}` Redisson 락만 건다 — 한 지점이라도 빠지면 그 경로는 락 없이 이 행을 건드리게 된다.

### 주문/결제

**orders** (요소 3c — 만료 배치와 결제 승인이 경합하는 락 대상)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_number | VARCHAR(50) UNIQUE | |
| member_id | BIGINT FK | |
| total_amount | BIGINT | |
| status | ENUM | CREATED / STOCK_RESERVED / PAYMENT_PENDING / PAID / FAILED / CANCELED / PARTIAL_CANCELED / **EXPIRED** |
| idempotency_key | VARCHAR(100) UNIQUE | |
| created_at | DATETIME | |

> `EXPIRED`는 요소 3c(재고 선점-만료 배치)를 위해 추가한 상태다. `PAYMENT_PENDING`으로 일정 시간(설정값, 예: 10분) 이상 머문 주문을 배치가 만료시킬 때 이 상태로 전이한다.

**order_item**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| product_id | BIGINT FK | |
| quantity | INT | |
| unit_price | BIGINT | 주문 시점 가격 스냅샷 |

> 도메인 규칙 7번대로 한 주문에는 한 상품 종류만 담기므로, 지금은 order 1건당 order_item도
> 항상 1건이다(수량만 자유). 테이블을 order_id 1:N으로 둔 건 스키마 자체를 미리 못박지
> 않기 위함이고, 지금 API(`POST /api/orders`)는 요청 자체를 단일 상품+수량으로만 받는다.

**payment** (환불 가능 잔여 금액 컬럼 포함 — 도메인 규칙 11)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| payment_method | ENUM | CARD (현재는 카드만 지원, 추후 확장) |
| pg_provider | VARCHAR(50) | |
| pg_transaction_id | VARCHAR(100) | |
| idempotency_key | VARCHAR(100) UNIQUE | |
| amount | BIGINT | |
| refundable_amount | BIGINT | **환불 가능 잔여 금액.** 승인 완료 시 `amount`와 동일하게 초기화, 환불 처리마다 이 컬럼에서 직접 차감 |
| status | ENUM | READY / IN_PROGRESS / DONE / FAILED / CANCELED / PARTIAL_CANCELED / TIMEOUT_UNKNOWN |
| approved_at | DATETIME nullable | |
| created_at | DATETIME | |

> `refundable_amount`를 컬럼으로 두는 이유: `SUM(payment_cancel.cancel_amount)` 집계로 매번 계산하면 동시 부분환불 요청 시 초과 환불 위험이 있다. 컬럼화 + 환불 처리 시 이 `payment` 행에 비관적 락을 걸어 순차 처리하면 방지된다.

**refund_request**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| payment_id | BIGINT FK | |
| request_amount | BIGINT | |
| status | ENUM | REQUESTED / PROCESSING / COMPLETED / FAILED / REJECTED |
| idempotency_key | VARCHAR(100) UNIQUE | 재시도 시 PG 측 중복 환불 방지 |
| requested_at | DATETIME | |
| processed_at | DATETIME nullable | |

> 환불 **요청** 생성(insert)은 동시성 이슈 없음. 문제는 동일 결제 건에 대한 환불 **처리**가 동시에 여러 건 들어올 때만 발생 — `payment.refundable_amount`에 락을 거는 이유가 여기 있다.

**payment_cancel**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| payment_id | BIGINT FK | |
| refund_request_id | BIGINT FK nullable | 환불 요청을 통한 처리인 경우 연결, 관리자 강제 취소는 nullable |
| cancel_amount | BIGINT | 부분취소/부분환불 지원 |
| reason | VARCHAR(200) | |
| canceled_at | DATETIME | |

### 정산

**settlement**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| payment_id | BIGINT FK | |
| vendor_id | BIGINT FK→vendor | 정산금을 지급받을 업체 |
| amount | BIGINT | 정산 대상 금액(결제 금액) |
| pg_fee_amount | BIGINT | PG 수수료 |
| platform_fee_amount | BIGINT | 플랫폼(우리) 수수료 |
| net_amount | BIGINT | 업체 실지급액 = amount − pg_fee_amount − platform_fee_amount |
| status | ENUM | PENDING / SETTLED / FAILED |
| settled_at | DATETIME nullable | 배치가 실제 업체에게 지급 처리한 시각 |
| created_at | DATETIME | 사가의 "정산 데이터 적재" 단계에서 PENDING으로 생성되는 시각 |

> 실제 지급 처리는 별도 배치 잡(요소 5)이 이 테이블을 읽어서 수행한다. 한 주문은 한 상품 종류만 담을 수 있어서(도메인 규칙 7번) 업체도 자동으로 하나고, payment 1건당 settlement 1건이다.

### Saga / Outbox

**saga_log** (요소 1)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| step | ENUM | PAYMENT / POINT_EARN / STOCK_CONFIRM / NOTIFICATION / SETTLEMENT |
| status | ENUM | STARTED / SUCCESS / FAILED / COMPENSATING / COMPENSATED |
| error_message | VARCHAR(500) nullable | |
| created_at | DATETIME | |

**outbox_event** (요소 2부터 실제 사용, 그 전엔 테이블만 있고 코드에서 안 씀)
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
payment 1---N refund_request
product 1---1 stock
product 1---N order_item
vendor 1---N product (공급 업체)
payment 1---N settlement
vendor 1---N settlement (지급 대상)
```

---

## API

### 이 레포가 제공하는 API

상품/포인트 조회 같은 "화면용" API는 만들지 않는다. 테스트 회원(id 1~10, `test{id}@test.com`/`test{id}`)과 상품/재고는 Flyway 시드 데이터(`V2__seed_data.sql`, vendor id 1~3, product id 1~10은 vendor 1~3에 분산 공급, 재고는 1~500으로 다양하게)로 고정 ID를 심어두고 사용한다.

| Method | Endpoint | 설명 | 관련 요소 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 (재고 임시 예약) | 1, 3a, 3c |
| POST | `/api/payments` | 결제 승인 요청 | 1, 3c |
| GET | `/api/payments/{id}` | 결제 상태 조회 | 1 |
| POST | `/api/payments/{id}/cancel` | 결제 취소(전체) | 1 |
| POST | `/api/payments/{id}/refund-requests` | 부분환불 요청 생성 | 도메인 규칙 11 |
| POST | `/api/webhooks/pg` | PG 웹훅 수신 | 1 |

`Idempotency-Key` 헤더는 `/api/orders`, `/api/payments`, `/api/payments/{id}/refund-requests` 전부 필수.

### 이 레포가 호출하는 외부 PG API (계약, `PgClient` Feign 인터페이스로 구현)

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/mock-pg/approve` | 승인 요청 |
| POST | `/mock-pg/cancel` | 취소/환불 요청 |
| GET | `/mock-pg/transactions/{txId}` | 거래 상태 조회 — 망취소 재확인용. `PaymentService`에서 동기로 호출하지 않고, `TIMEOUT_UNKNOWN` payment를 재확인하는 배치(요소 3c와 함께 구현)가 사용 |
| (수신) `POST /api/webhooks/pg` | PG가 승인 완료 후 보내는 콜백 |

---

## 개발 단계 (요소별로 명확히 구분해서 진행한다)

**단계 1 — 설계/스캐폴딩**
- ERD 확정, Flyway 마이그레이션 작성
- 주문/결제 상태 머신 확정 (불가능한 전이 정의, `EXPIRED` 포함)
- 프로젝트 스캐폴딩, Docker Compose(MySQL + Redis만, RabbitMQ는 아직 X)

**단계 2 — 도메인 기본 구현**
- 상품/재고/회원/포인트 도메인
- 주문 생성 API (재고 임시 예약)
- 결제 승인 API + 외부 PG 동기 연동
- 멱등키 처리 (Redis SETNX + DB unique constraint)

**단계 3 — 요소 1: 결제 코어 정합성 (안정성, Before/After 없음, `PaymentSagaOrchestrator`)**
```
결제 승인 요청
  → PG 승인 (PgClient, 트랜잭션 밖, 동기)
  → 결제 상태 저장 (payment.toDone(), 자기 트랜잭션)
  → PaymentSagaOrchestrator.run(payment) — 오케스트레이터가 다음 스텝을 순서대로 직접 호출
      → 포인트 적립 (PointService, 자기 트랜잭션)
      → 재고 확정 (StockService, 자기 트랜잭션)
      → 알림 발송 (NotificationService, 트랜잭션 없음 — 지금은 로그만 남기는 스텁)
      → 정산 데이터 적재 (SettlementService, 자기 트랜잭션)
  → 응답
```
- 오케스트레이터가 각 스텝을 직접 순서대로 호출하고, 스텝마다 `SagaLog`에 STARTED/SUCCESS/FAILED를 직접 기록한다. RabbitMQ, Outbox 둘 다 이 단계에서 쓰지 않는다.
- POINT_EARN/STOCK_CONFIRM 실패 시에만 보상: 지금까지 성공한 스텝을 역순으로 되돌리고(포인트 회수 → PG 취소 호출 → `payment.toCanceled()`), `SagaLog`에 COMPENSATING/COMPENSATED를 남긴다. NOTIFICATION/SETTLEMENT 실패는 보상 대상에서 제외하고 FAILED만 기록.
- PG 취소 호출 자체가 실패해도 로컬 상태(결제 취소, 포인트 회수)는 그대로 반영한다 — PG 통지는 best-effort.
- 망취소 로직: `PgClient.approve`가 재시도까지 다 실패하면 `PaymentService.handleApprovalTimeout`이 결제를 `TIMEOUT_UNKNOWN`으로 남기고 바로 응답한다. 요청 스레드에서 동기 재확인하지 않는다.
- 멱등키 응답 캐싱(도메인 규칙 8번): 승인 성공 시 Redis에 `{idempotencyKey → 응답}`을 TTL과 함께 저장 — 아직 미구현.
- **필수 통합테스트**: 동시 주문 100건 → 중복 승인 0건 / 포인트 적립 강제 실패 → 보상 트랜잭션으로 결제 취소·재고 원복 완료 검증 (CI 등록, 완화 금지) — 지금은 단일 스텝 실패 보상 케이스만 있고 동시 100건 부하 케이스는 아직 없음

**단계 4 — 요소 2: 비동기 처리 (성능, Before/After — 여기서 처음 RabbitMQ + Outbox 도입)**
- 단계 3 구조 그대로 k6 baseline 측정 (TPS/p50/p95/p99/에러율)
- Outbox 테이블 + Relay 신규 도입
- RabbitMQ 신규 도입, Outbox Relay → 큐 발행 → 포인트/재고/알림/정산 컨슈머 분리
- 모든 컨슈머는 도메인 규칙 10번(이벤트 순서 유효성 검증)을 처음부터 지켜서 만든다 — 이건 성능 실험 대상이 아니라 컨슈머가 반드시 지켜야 하는 정합성 요건.
- PG 웹훅 수신도 즉시 200 응답 후 큐 적재로 전환
- 재측정 후 Before/After 비교

**단계 5 — 요소 3a: 재고 차감 동시성 (성능, Before/After — 축은 "비관적 락 단독 vs Redisson 분산락 단독")**
```java
// Before — 처음부터 이렇게 짠다 (재고 접근은 잦고 충돌도 잦은 자원이라 비관적 락이 자연스러운 시작점)
@Transactional
public void deductStock(Long productId, int qty) {
    Stock stock = stockRepository.findByIdForUpdate(productId); // SELECT ... FOR UPDATE, 대기 동안 DB 커넥션 점유
    stock.deduct(qty);
}
```
```java
// After — DB 락(FOR UPDATE)은 걷어내고 Redisson 분산락 하나로 대체한다. "대기"는 전부 Redis 쪽에서
// 일어나고, 락 안에서는 평범한 SELECT + 갱신만 한다.
public void deductStock(Long productId, int qty) {
    RLock lock = redissonClient.getLock("lock:stock:" + productId);
    lock.lock(); // 락 대기는 Redis pub/sub, DB 커넥션 점유 없음
    try {
        stockPersistenceService.deductWithoutLock(productId, qty); // 짧은 트랜잭션, FOR UPDATE 없이 갱신 + 커밋
    } finally {
        lock.unlock();
    }
}
```
- 한정판 상품 특성상 동일 상품(핫 아이템)에 동시 주문이 몰려 충돌이 잦다. 낙관적 락 재시도 대신 처음부터 비관적 락(`SELECT ... FOR UPDATE`)으로 같은 상품 행에 대한 요청을 DB 트랜잭션 단에서 직렬화하는 걸 **Before**로 삼는다 — 이건 3c·환불 처리와 같은 이유로 자연스러운 선택.
- **Before의 한계**: `FOR UPDATE` 대기는 그 시간만큼 DB 커넥션 풀의 커넥션을 붙들고 있다. 충돌이 심한 핫 아이템에 요청이 몰리면 대기 트랜잭션들이 커넥션 풀을 소진시켜, 그 상품과 무관한 다른 요청까지 커넥션을 못 받아 지연되는 문제로 번진다.
- **After (Redisson 분산락 단독)**: 같은 상품 키(`lock:stock:{productId}`)에 대한 대기를 Redis 쪽으로 옮기고, `FOR UPDATE`는 아예 제거한다. 락을 못 얻은 요청은 DB 트랜잭션을 아예 열지 않으므로 DB 커넥션을 점유하지 않는다. 락을 얻은 요청만 짧게 DB 트랜잭션을 열어 곧장 갱신 후 바로 커밋한다 — FOR UPDATE를 남겨둬도 이 안에서는 항상 비경합으로 즉시 풀리기 때문에(같은 순간엔 이 락을 쥔 스레드가 전역에서 하나뿐이라) 순수 오버헤드만 남고 보호 효과가 없어서 걷어냈다.
- **적용 범위는 위 예시(`deductStock`)로 대표되는 주문 생성 시점만이 아니다.** 같은 `stock` 행은 결제 승인 성공 시 reserved→sold로 확정하는 지점(`confirmForOrder`)과 결제 실패·만료로 reserved를 원복하는 지점(`releaseForOrder`)에서도 바뀐다 — 이 두 지점도 반드시 같은 `lock:stock:{productId}` 락으로 감싼다. Redisson 락은 애노테이션이 붙은 메서드 단위로만 걸리는 거라, 주문 생성만 락을 걸고 확정/원복을 빼먹으면 그 두 경로가 서로 그리고 주문 생성과 락 없이 경합하게 되어 FOR UPDATE 없이는 바로 lost update로 이어진다.
- **알려진 잔여 리스크**: `tryLock(waitTime, leaseTime, ...)`처럼 명시적 `leaseTime`을 넘기면 Redisson 워치독(보유 스레드가 살아있는 동안 락을 자동 갱신)이 붙지 않는다. 임계구역이 `leaseTime`을 넘겨 실행되면 락이 자동 해제되어 두 요청이 동시에 들어올 수 있는데, FOR UPDATE 백스톱이 없는 지금은 이 경우를 못 막는다. 임계구역이 단순 CRUD 수준이라 감수하는 트레이드오프이며, 걱정되면 `leaseTime`을 넉넉히 늘리거나 워치독을 쓰도록 바꾸는 걸 고려한다.
- k6 시나리오 A(정상, 서로 다른 상품)/B(충돌, 동일 한정판 상품 집중) 분리 측정. **핵심 지표는 TPS·p95뿐 아니라 DB 커넥션 풀 대기/타임아웃 발생 여부** — 이게 이 실험의 실제 증거다.
- **트레이드오프 필수**: 시나리오 A(충돌 거의 없음)에서는 분산락이 Redis 왕복이라는 불필요한 오버헤드가 될 수 있다. 이 경우 분산락 적용 전/후 TPS를 비교해 "충돌이 잦을 걸로 예상되는 핫 아이템에만 조건부로 분산락을 적용한다"는 설계 판단까지 결과로 뒷받침한다.
- Before는 `stock` 행에만 DB 락을 건다(도메인 규칙 9번과 같은 원칙 — 여러 테이블에 동시에 비관적 락을 걸지 않는다). After는 그 DB 락 자체를 걷어내고 Redisson 락 하나로 대체한다. 락 충돌/대기 횟수는 별도 테이블 없이 Micrometer 메트릭으로 관찰.

**단계 6 — 환불 처리 동시성 (도메인 규칙 11, 처음부터 비관적 락)**
- `refund_request`, `payment.refundable_amount` 구현
- 환불 처리 API(`/api/payments/{id}/refund-requests`)는 `payment` 행에 비관적 락을 걸고 `refundable_amount`를 직접 차감 — 처음부터 이렇게 짠다, 나중에 개선하는 구조가 아님
- **검증**: 동일 결제 건에 부분환불 요청 다건을 동시에 발생시켜 초과 환불 0건, 총 환불액이 원 결제 금액을 넘지 않는지 통합테스트로 확인

**단계 7 — 요소 3c: 재고 선점-만료 경합 (안정성, Before/After 없음, 처음부터 완성)**
- 일정 시간(설정값) `PAYMENT_PENDING` 상태로 머문 주문을 만료시키는 스케줄러(`OrderExpirationScheduler`) 신규 구현
- 처리 순서: ① 만료 대상 `orders`를 조회 → ② 연결된 `payment`가 `TIMEOUT_UNKNOWN`이면 `/mock-pg/transactions/{txId}`로 먼저 실제 상태 재확인(PG가 이미 승인했었다면 `orders`를 `PAID`로 정정하고 만료 대상에서 제외, 이 경우는 반드시 별도 로그로 남김) → ③ 안전이 확인된 건에만 `orders` 행에 비관적 락을 걸고 `EXPIRED`로 전이
- 재고 반환은 도메인 규칙 9번대로 별도 트랜잭션/이벤트로 처리(만료 배치가 `orders` 외 다른 테이블까지 같이 락 잡지 않음)
- 결제 승인 흐름(`PaymentService`)도 PG 호출 전에 먼저 `orders` 행 락을 짧게 획득해 `PAYMENT_PENDING`인지 확인하는 단계를 추가 — 이미 `EXPIRED`면 PG 호출 자체를 하지 않고 "주문이 만료되었습니다" 실패 응답
- **검증**: 동일 주문에 만료 처리와 결제 승인 요청을 동시에 발생시키는 레이스 컨디션 재현 테스트에서, 두 흐름 중 정확히 하나만 반영되는지 반복 확인

**단계 8 — 요소 4: 서킷브레이커 (안정성, Before/After 없음 — Retry는 이미 있음, CircuitBreaker/TimeLimiter만 추가)**
- Before(=현재 상태): `PgClient`에 `@Retry`만 있음. 재시도를 다 써도 실패가 반복되면 요청 스레드가 그 시간만큼 계속 붙잡혀 있다가 실패하고, 이게 반복되면 톰캣 스레드풀이 서서히 고갈 → 동시간대 무관한 다른 주문의 결제 요청까지 지연/타임아웃
- 장애 주입(지연/에러) 시 스레드풀 고갈로 무관한 다른 주문까지 영향받는 것을 먼저 재현해서 확인
- 개선: `PgClient`에 이미 있는 `@Retry`(name = "pgClient")와 같은 인스턴스명으로 `@CircuitBreaker`/`TimeLimiter` 추가 — 실패율이 임계치를 넘으면 재시도조차 하지 않고 즉시 폴백(결제 대기 큐 적재 등)으로 빠짐
- PG 실패 원인 분류(외부 PG 호출 섹션에서 이미 적용한 일시적 장애 vs 결정적 실패 구분)를 서킷브레이커 실패율 집계에도 반영 — 4xx(유저 귀책)는 `recordExceptions`/`ignoreExceptions` 설정으로 실패율 카운트에서 제외해 정상 상황에서 서킷이 오작동하지 않게 함
- Rate Limiting(토큰버킷)도 이 단계에서 함께 구현
- **검증**: 장애 주입 전/후 무관한 다른 주문 요청의 응답 시간 변화를 시계열로 비교, PG 실패 유형별(유저귀책/시스템에러) 재시도·서킷 동작이 설계한 대로인지 확인

**단계 9 — 요소 5: 정산 배치 + 대사 (안정성, Before/After 없음)**
- PG가 정산 파일을 생성하는 기능은 `mock-pg` 레포에서 구현 (이 레포 밖)
- 이 레포 또는 별도 배치 잡이 정산 파일과 내부 `payment`/`settlement` 데이터를 Chunk 단위로 대조, 금액 불일치·상태 불일치·한쪽에만 존재 4종을 탐지
- 실패 시 마지막 성공 Chunk부터 재시작 가능하도록 설계
- **검증**: 정산 파일에 의도적으로 불일치 케이스 N건을 주입한 뒤, 배치가 N건 전부 탐지하는지 확인

---

## 성능 실험 원칙 (요소 2, 3a — 성능형 항목에만 적용)

- 최소 3회 반복 측정 후 평균/중앙값 사용
- 테스트 환경(리소스 제한, 데이터량) 기록해 재현성 확보
- TPS, p50/p95/p99, 에러율 기본 기록
- "개선됐다"만 쓰지 말고 트레이드오프도 같이 기록
- **안정성형 항목(1, 3c, 4, 5)은 이 원칙을 적용하지 않는다.** 대신 통합테스트/레이스 재현 테스트의 통과 여부(중복 0건, 초과 0건, 정확히 하나만 반영 등)로 증명한다. 성능 수치를 억지로 붙이지 않는다.

---

## 커맨드

```bash
./gradlew test                 # H2 기반 테스트 실행 (테스트 스타터: -actuator-test, -data-jpa-test, -data-redis-test, -flyway-test, -validation-test, -webmvc-test)
./gradlew bootRun              # 로컬 실행 (MySQL/Redis는 docker-compose로 먼저 기동)
./gradlew flywayMigrate        # DB 마이그레이션
```