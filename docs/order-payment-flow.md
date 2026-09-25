# 주문 생성 → 결제 → 정산 전체 프로세스

주문 하나가 생성돼서 완결(결제 완료 + 정산)되거나 끝나는(만료/실패/환불) 순간까지 코드가 실제로 하는 일을 시간순으로 적는다.

두 개의 배포 단위가 관여한다: 결제 API 서버(`PocketPay_Core`, 상시 구동), 배치(`PocketPay_Batch`, Jenkins 크론이 트리거하는 1회성 잡들). 둘 다 같은 MySQL `business` 스키마를 공유하고 마이그레이션 소유권은 Core의 Flyway에 있다 — Batch는 MyBatis로 읽고 쓰기만 한다. Batch는 이와 별개로 자체 배치 메타데이터 DB(Spring Batch 실행 이력 + `vendor_settlement_summary`)를 갖고 있다. PG는 실제 토스페이먼츠 API(`https://api.tosspayments.com`)를 쓴다 — `PocketPay_PG`(Mock PG)는 더 이상 이 흐름에 관여하지 않는다.

---

## 전체 흐름 요약

- 클라이언트가 체크아웃 진입 시 UUID로 `Idempotency-Key`를 만들어 `POST /api/orders`를 호출한다. Redis SETNX로 중복을 막고, `lock:stock:{productId}` 락 안에서 재고 확인 → 차감 → 주문 저장(`STOCK_RESERVED`) → 주문 아이템 저장을 한 트랜잭션으로 처리한다. `expires_at`(생성 시각 + 10분)을 함께 기록한다.
- 그 시각 전에 승인 요청이 오거나, 안 오면 배치(`orderExpirationJob`)가 나중에 `EXPIRED`로 바꾸고 재고를 반환한다.
- 승인 요청은 `paymentKey`/`orderNumber`/`amount`로 들어온다. 금액 위변조 검증 → 주문 잠금 → 만료·중복 재검증 → `PAYMENT_PENDING` + `Payment(READY)` 생성 + 포인트 예약까지 커밋한 뒤, DB 트랜잭션 밖에서 PG를 호출한다.
- PG 결과는 세 갈래: 4xx는 즉시 `FAILED`(주문·재고는 그대로 둬서 재시도 가능), 5xx/네트워크 재시도 소진은 `TIMEOUT_UNKNOWN`, 성공하면 **포인트 사용 확정 + 재고 확정 + 주문 `PAID`까지 하나의 트랜잭션으로 커밋**한다(4절) — 이 중 하나라도 실패하면 전부 롤백되고 결제는 `TIMEOUT_UNKNOWN`으로 남는다. **포인트 적립(캐시백)만 이 트랜잭션 밖으로 분리**해서, "적립할 것"이라는 기록만 같은 트랜잭션 안에 남기고 실제 잔액 반영은 커밋 후 비동기로 처리한다 — Saga 없이도 "핵심 결제"(돈이 오간 사실)는 트랜잭션 하나로 원자성을 보장하고, 부가적인 적립만 최종적 일관성(비동기 + 배치 재시도)으로 처리한다.
- 알림은 트랜잭션 커밋 후 비동기로 베스트에포트 발송한다(실패해도 재시도하지 않는다). 포인트 적립도 커밋 후 비동기로 처리하지만, 실패하면 배치가 재시도한다(4절) — 돈이 걸린 적립과 단순 UX인 알림을 다르게 취급한다.
- `TIMEOUT_UNKNOWN`으로 남은 결제는 배치(5.1절 재확인)가 PG에 재조회해서 같은 방식(포인트 사용 확정·재고 확정 포함 한 트랜잭션, 포인트 적립은 같은 재시도 대상으로 기록)으로 수렴시킨다 — 어느 경로로 완료되든 정합성 보장 방식이 같다.
- 완료된 결제는 배치가 정산 레코드를 만들고 가맹점별로 집계한다.
- 상태가 바뀔 때마다 트랜잭션 커밋 직후 Redis로 이벤트를 직접 발행해서 Admin 대시보드(SSE)에 전달한다(6절).
- 환불은 결제 단위로 독립 처리한다 — 주문 상태는 안 바뀌고 `PAID`로 남는다.

상태만 보면: 주문 `CREATED → STOCK_RESERVED → PAYMENT_PENDING → PAID`(+ `EXPIRED`), 결제 `READY → IN_PROGRESS → DONE`(+ `FAILED`/`TIMEOUT_UNKNOWN` → `DONE`/`FAILED`, 이후 `PARTIAL_CANCELED`/`CANCELED`).

---

## 식별자 정리

| 식별자 | 발급 주체 | 발급 시점 | 역할 |
|---|---|---|---|
| 클라이언트 Idempotency-Key | 클라이언트 | 체크아웃 진입 시 | 주문 생성 중복 방지, 회원 단위 스코프 |
| `orderNumber` | 서버 | 주문 생성 시 | 주문 정체성, PG 승인 요청에도 그대로 전달 |
| `paymentKey` | 클라이언트(PG 발급) | 승인 요청 시 | PG 쪽 결제 시도 식별(`Payment.pgTransactionId`) |
| 승인 멱등키 | 서버 | 승인 처리 직전 | `"confirm:" + orderNumber + ":" + paymentKey` |
| 환불 멱등키 | 서버 | 환불 처리 직전 | `"refund:" + orderNumber + ":" + quantity + ":" + reason + ":" + 5초 시간창` |

주문 생성 키만 클라이언트가 만든다 — 서버는 재시도인지 새 요청인지 판단할 근거가 없기 때문이다. 승인·환불은 서버가 이미 가진 값으로 결정적으로 파생한다.

### 상태 머신

```
Order:   CREATED → STOCK_RESERVED → PAYMENT_PENDING → PAID
                          │
                          └──(만료, 10분 뒤)──→ EXPIRED

Payment: READY → IN_PROGRESS → DONE ──(부분/전체 환불)──→ PARTIAL_CANCELED → CANCELED
                       │
                       ├──→ FAILED (PG 4xx 거절, 재시도 안 함)
                       └──→ TIMEOUT_UNKNOWN ──(재확인)──→ DONE 또는 FAILED
```

`OrderStatus.CANCELED`/`PARTIAL_CANCELED`/`FAILED`는 정의만 있고 세팅되지 않는다 — 취소·실패·환불은 전부 `Payment` 쪽으로만 표현한다. `REFUNDED`는 예전 Saga 보상 경로가 세팅하던 값이었는데, 그 경로를 걷어내면서 세팅하는 코드가 없어진 채로 남아있던 죽은 상태라 enum과 DB CHECK 제약에서 아예 제거했다(`V23__drop_order_refunded_status.sql`). `TIMEOUT_UNKNOWN`은 "결제가 실패했다"가 아니라 "PG 응답을 못 받았다"는 뜻이다.

---

## 1. 주문 생성 (`POST /api/orders`)

체크아웃 화면 진입 시 클라이언트가 UUID로 `Idempotency-Key`를 만들어 들고 온다.

1. 캐시된 성공 응답이 있으면(Redis) 그대로 반환한다(**COMPLETED**). 없으면 Redis `SETNX`를 시도한다(`"order:" + memberId`로 회원 단위 스코프).
   - 성공(**PROCESSING**) → 주문 생성 진행. 끝나면 성공 여부와 무관하게 락을 반납하고, 성공했을 때만 응답을 캐싱한다.
   - 실패 → 캐시를 한 번 더 확인해서 **COMPLETED**면 그 응답, 아직 **PROCESSING**이면 즉시 409(`OrderErrorCode.ORDER_CREATION_IN_PROGRESS`)를 반환한다 — 승인과 달리 대기하지 않는다. 멱등성 원칙대로면 처리 중인 요청도 대기했다가 원 요청과 같은 응답을 돌려줘야 하지만, 여기선 그렇게 하지 않는다: `lock:stock:{productId}`는 이 상품을 사려는 모든 사람이 공유하는 자원이라(대기시간 자체는 `tryLock`으로 상한이 있지만), 중복 요청까지 그 위에서 대기시키면 안 그래도 붐비는 병목에 대기 스레드를 더 얹는 꼴이 된다. 그래서 "같은 응답을 준다"는 원칙을 포기하고 "처리 중이니 다시 확인해라"로 타협했다 — 그 대기 부담을 서버 스레드 대신 클라이언트의 재조회로 넘기는 것이다.
2. `lock:stock:{productId}` 락을 잡기 **전에** 재고를 가볍게 조회해서, 확실히 품절이면 락 대기 없이 즉시 거절한다(`preCheckStockAvailability`). 락 밖에서 읽는 값이라 더티 리드이고, 4번의 실제 차감·재검증을 대체하지 않는다 — 뻔한 실패 요청이 락 대기 줄에 서지 않게 하는 최적화다.
3. `lock:stock:{productId}` Redisson 분산락을 잠근다 — 락이 트랜잭션 커밋까지 다 감싸야, 트랜잭션이 안 끝난 시점에 락만 먼저 풀려서 다른 스레드가 재고를 잘못된 값으로 읽는 걸 막을 수 있다.
4. 한 트랜잭션 안에서: 재고 확인·차감(`Stock.reserve`, 부족하면 `INSUFFICIENT_STOCK`) → 주문 저장(`CREATED` 즉시 `STOCK_RESERVED`로 전이) → 주문 아이템 저장.
5. 트랜잭션 커밋 후에야 락을 반납한다.
6. `orders`엔 `(member_id, idempotency_key)` 복합 유니크 제약도 있어(`DataIntegrityViolationException` 캐치), DB 레벨에서 한 번 더 막는다.

주문 생성 시점에 `expires_at`(생성 시각 + `order.reservation-timeout-minutes`, 기본 10분)을 계산해서 저장하고 응답에도 노출한다(`OrderResponse.expiresAt`). 그 전에 승인되거나, 지나면 `orderExpirationJob`이 `status='STOCK_RESERVED' AND expires_at < NOW()`인 건을 찾아 `EXPIRED`로 바꾸고 재고 예약 때와 같은 `lock:stock:{productId}` 락으로 재고를 반환한다. Core와 배치가 같은 `expires_at` 값을 보므로 드리프트가 없다. 이미 `PAYMENT_PENDING`으로 넘어간 주문은 조건에 안 걸려 스킵된다(2절의 경합 방지 핵심).

---

## 2. 결제창 호출과 승인 직전 재검증

결제창 인증 후 클라이언트는 `paymentKey`(PG 발급값), `orderNumber`, `amount`로 승인 API(`POST /api/payments/{orderNumber}`)를 호출한다.

승인 로직이 제일 먼저 `order.expiresAt`을 비교해서 지났으면, 배치가 아직 안 돌았어도 그 자리에서 재고를 반환하고(재고 예약 때와 같은 `lock:stock:{productId}` 락으로 `StockRestorationService.restore()`) 주문을 `EXPIRED`로 전환한 뒤 `OrderExpiredException`(`ORDER_EXPIRED`)을 던진다(Lazy Expiration). `PaymentStateService.initiate()`는 `@Transactional(noRollbackFor = OrderExpiredException.class)`라서, 이 예외가 던져져도 방금 만든 재고 반환·상태 전환은 롤백되지 않고 그대로 커밋된다. `noRollbackFor`를 `CustomException` 전체가 아니라 이 전용 서브클래스 하나로 좁혀둔 이유는, 같은 메서드 뒤쪽의 `pointReservationService.reserve()`도 레이스로 `CustomException(INSUFFICIENT_POINT_BALANCE)`를 던질 수 있는데 그건 정상적으로 전체 롤백돼야 하기 때문이다(포인트 예약 실패인데 `Payment`/주문 상태 변경만 커밋되면 안 됨). 재고 반환 자체는 `@DistributedLock`이 걸린 별도 빈(`StockRestorationService`) 호출인데, 이 락 애노테이션은 호출부에 이미 트랜잭션이 있으면 거기 합류한다(`AopForTransaction`이 `REQUIRED`) — 그래서 재고 반환도 `initiate()`와 같은 트랜잭션의 일부로 원자적으로 커밋·롤백된다. 주문 행은 `initiate()`가 이미 잡고 있는 `SELECT ... FOR UPDATE` 락 안에서 상태만 바뀌므로 같은 행을 두 번 잠그는 충돌은 없다. 만료 배치(Active Expiration)의 조건부 UPDATE(`WHERE status='STOCK_RESERVED'`)는 이미 `EXPIRED`로 바뀐 주문을 걸러내므로 중복 반환도 없다 — 누군가 승인을 시도한 주문은 이 즉시 회수(Lazy)가 처리하고, 아무도 시도하지 않고 방치된 주문은 배치(Active)가 나중에 회수한다.

재고 반환이 락 경쟁(같은 상품에 몰린 다른 주문 생성 요청들)으로 `lock:stock:{productId}`를 `lock.default-wait-time-seconds`(5초) 안에 못 잡으면, `DistributedLockAop`가 `CustomException(REQUEST_TIMEOUT)`을 던진다 — 이건 `OrderExpiredException`이 아니라서 `noRollbackFor` 대상이 아니고, `initiate()` 전체가 기본 롤백된다. 즉 재고 반환도 주문 만료 전환도 일어나지 않은 채 깨끗하게 실패하고(주문은 그대로 `STOCK_RESERVED`), 호출자는 `ORDER_EXPIRED` 대신 `REQUEST_TIMEOUT`(둘 다 409)을 받는다. 이 시도가 계속 락을 못 잡아도 주문은 여전히 만료 배치의 조건(`STOCK_RESERVED` + 지난 `expires_at`)에 걸려있으므로 Active Expiration이 나중에 회수한다 — Lazy가 락 경쟁에 밀려도 재고가 영영 묶이지는 않는다.

---

## 3. 결제 승인 처리

### 3.1 멱등키와 중복 요청

승인 멱등키(`confirm:orderNumber:paymentKey`)는 서버가 결정적으로 만든다 — 같은 주문·같은 paymentKey면 항상 같은 키가 나온다.

1. 캐시된 성공 응답 있으면 반환.
2. 없으면 `SETNX`. 실패하면 캐시된 결과가 나올 때까지 대기한다.
3. 락을 획득한 요청만 실행하고, 확정된 결과(`DONE`/`FAILED`)만 캐싱한다 — `TIMEOUT_UNKNOWN`은 아직 결과를 모른다는 뜻이라 캐싱하지 않는다(9절).

결제는 주문과 달리 대기시킨다 — 이 락(주문 행 잠금)은 이 결제 시도와 그 중복 요청끼리만 경합하는 격리된 자원이라, 대기시켜도 다른 사용자의 요청에 영향이 없다. 그래서 멱등성 원칙(같은 요청은 같은 응답)을 타협 없이 지킬 수 있다 — 처리 중인 원 요청이 끝날 때까지 기다렸다가 **똑같은 결과**를 돌려준다. 화면에서 "처리중"이 뜬 상태로 사용자가 더블클릭해도, 두 요청 모두 로딩 상태로 있다가 원 요청이 끝나면 거의 동시에 같은 응답을 받으므로 화면이 꼬이지 않는다. 단, 캐싱은 `DONE`/`FAILED`일 때만 하므로(3번), 원 요청이 `TIMEOUT_UNKNOWN`으로 끝나거나 예외로 거절되면 캐시가 안 채워져서 대기 중이던 중복 요청은 결국 자기 타임아웃으로 `PaymentErrorCode.PAYMENT_APPROVAL_TIMEOUT`(환불은 `REFUND_TIMEOUT`)을 받는다.

다른 회원의 `paymentKey`를 다른 주문에 제출해도 캐시 충돌로 걸리지 않고 새 승인 시도로 처리된다 — 승인 전에 PG에 `paymentKey` 소유권을 되물어 대조하지 않기 때문이다(9절).

### 3.2 `doApprove()` 흐름

1. 주문 조회 + 소유자 검증.
2. 포인트 사용 검증(잔액, 주문 금액 초과 여부).
3. **위변조 검증**: `pgAmount = order.totalAmount - usePointAmount`가 클라이언트가 보낸 `amount`와 다르면 PG를 부르지 않고 즉시 거절.
4. `initiate()` — 짧은 트랜잭션 하나: 주문 행 잠금 → 이미 `IN_PROGRESS`인 결제 있으면 즉시 거절(중복 승인 차단) → 2절의 만료 재검증 → `PAYMENT_PENDING` 전환 → `Payment(READY)` 저장 → 포인트 예약(아직 실차감 아님) → 상태 변경 이벤트 발행(6절) → `IN_PROGRESS`.
5. 포인트 전액 결제(`pgAmount == 0`)면 PG 호출을 생략하고 바로 완료 처리(`markDone()`, 4절)로.
6. PG 호출은 여기서부터 DB 트랜잭션 밖(OpenFeign + Resilience4j): 5xx·네트워크만 재시도, 4xx는 재시도 안 함(서킷 실패율 집계에서도 제외).
   - **4xx**: 즉시 `Payment.FAILED`, 주문·재고는 그대로 둬서 새 `paymentKey`로 재시도 가능.
   - **5xx/네트워크 재시도 소진**: 곧바로 `TIMEOUT_UNKNOWN`으로 응답하지 않고, 그 자리에서 PG에 **한 번 더, 즉시 재조회**(`PgClient.inquire()`, `GET /v1/payments/{paymentKey}`)를 시도한다 — `DONE`으로 확인되면 바로 결제를 완료 처리하고, `ABORTED`/`EXPIRED`/`CANCELED`처럼 명확한 실패로 확인되면 바로 실패 처리한다. 이 즉시 재조회 자체가 실패하거나 결과가 여전히 불명확하면(`READY`/`IN_PROGRESS`/`WAITING_FOR_DEPOSIT` 등) 그때 비로소 `Payment.TIMEOUT_UNKNOWN`으로 응답하고, 최종 확인은 5.1절 배치에 맡긴다. 승인 재시도가 실패한 대부분의 경우 PG 쪽은 이미 결론이 나 있는 경우가 많아서, 이 즉시 재조회가 배치의 `thresholdMinutes` 대기 없이 그 자리에서 결과를 확정시켜주는 경우가 많다 — 배치는 이 즉시 재조회조차 실패한, 정말로 애매한 나머지 건들을 위한 최종 안전망이다.
7. 성공 시 `markDone()`(4절) — 포인트 사용 확정 + 재고 확정 + 주문 `PAID`까지 **한 트랜잭션**으로 커밋하고, 포인트 적립(캐시백)은 "적립할 것"이라는 기록만 같은 트랜잭션에 남긴다. 이 트랜잭션이 실패하면(PG는 이미 승인됨) 알림 로그를 남기고 `TIMEOUT_UNKNOWN`으로 폴백시킨 뒤 예외를 던진다(회수는 5.1절).
8. `markDone()`이 커밋된 뒤 알림과 포인트 적립을 각각 비동기로 던지고 바로 성공 응답을 반환한다 — **이후로는 결제 자체를 되돌리지 않는다.**

---

## 4. 결제 완료 처리 — `markDone()`과 포인트 적립의 분리

`PaymentStateService.markDone()`이 포인트 사용 확정, 재고 확정, 주문 `PAID` 전환, 그리고 포인트 적립 "기록"(아래 참고)을 **전부 같은 `@Transactional` 메서드 안**에서 순서대로 실행한다. 하나라도 실패하면 전체가 롤백되고, 호출부(3.2의 7번)가 그 예외를 잡아 결제를 `TIMEOUT_UNKNOWN`으로 남긴다 — 부분 성공 상태가 DB에 남는 경우가 없다.

- `point_balance`/`point_reservation`/`point_ledger`, `stock`, `payment`, `orders` 전부 같은 MySQL `business` 스키마 안에 있어서, 원래 하나의 ACID 트랜잭션으로 묶을 수 있는 것들이었다. 예전엔 이걸 일부러 여러 개의 짧은 트랜잭션으로 쪼개고(`PaymentSagaOrchestrator`) `saga_log`로 단계별 성공 여부를 추적하는 Saga 패턴을 썼는데, 정작 분산 트랜잭션이 필요한 이유(여러 리소스에 걸친 트랜잭션 경계)가 없어서 복잡성만 더했다 — Saga 계열 코드(`PaymentSagaOrchestrator`, `SagaLogService`, `saga_log` 테이블, 포인트/재고 재시도 배치, `SagaCompensationService`)를 전부 걷어내고 이 방식으로 바꿨다.
- 재고 확정(`StockService.confirmForOrder()` → `StockLockingService.confirm()`)은 재고 예약 때와 같은 `lock:stock:{productId}` 락을 쓴다. `@DistributedLock`의 트랜잭션 전파가 `REQUIRED`라서(`AopForTransaction`), `markDone()`의 트랜잭션에 합류해 같이 커밋·롤백된다 — 락 획득 자체가 실패하면(경쟁 심함) `markDone()` 전체가 예외로 실패해서 롤백된다. 재고 확정은 "얼마나 팔렸는지"를 나타내는 핵심 장부라서 결제 완료와 분리하지 않고 그대로 원자적 트랜잭션 안에 남겨뒀다.
- `reserved_quantity`가 부족해서 재고 확정이 실패하는 경우(진짜 오버셀)는, 재고 예약 시점에 이미 가용 수량을 검증했으므로 정상 업무 흐름에서는 사실상 버그로만 발생한다. 이 경우 트랜잭션이 롤백되고 결제는 `TIMEOUT_UNKNOWN`으로 남아 5.1절 재확인 배치가 계속 재시도한다 — 원인이 진짜로 안 고쳐지는 버그라면 영원히 재시도만 반복된다(9절, 한도·자동 보상 없음).
- PG 승인은 이미 성공한 뒤라서, 이 트랜잭션이 실패해도 PG에 돈은 이미 들어간 상태다. `criticalAlertService.alertPgApprovedButPersistFailed()`가 Slack 치명 알림을 남기고 `TIMEOUT_UNKNOWN`으로 폴백시킨다 — 5.1절 재확인 배치가 PG 조회로 다시 `DONE`을 확인하면 같은 트랜잭션을 다시 시도해 정상 완료시킨다.

### 포인트 적립(캐시백)은 왜, 어떻게 분리했나

포인트 사용 확정·재고 확정과 달리 포인트 적립은 "이번 결제가 유효한가"를 좌우하는 값이 아니라 부가적인 보상이다. 원래는 이것도 `markDone()` 안에서 동기로 잔액을 올렸는데, 그러면 캐시백 적립 쪽의 사소한 문제(예: 락 경합)가 이미 PG 승인이 끝난 결제 전체를 `TIMEOUT_UNKNOWN`으로 떨어뜨릴 수 있었다 — 부가 기능이 핵심 결제를 볼모로 잡는 구조였다. 그래서 `markDone()`은 실제 적립 대신 **"이 주문은 이만큼 적립해야 한다"는 사실만 `point_earn_log`에 `PENDING` 상태로 기록**하고, 진짜 잔액 반영은 트랜잭션 밖에서 처리한다.

- 기록 자체(`PointEarnLogService.record()`)는 `markDone()`과 같은 트랜잭션에 있으므로, `markDone()`이 롤백되면 이 기록도 함께 사라진다 — "결제는 안 됐는데 적립 예정만 남는" 상태는 없다.
- 커밋 후 `PointEarnRequestedEvent` → `PointEarnRequestedEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)가 `PointEarnAsyncService.applyAsync()`를 비동기로 호출한다. 이 메서드가 `point_earn_log` 행을 잠그고(`PESSIMISTIC_WRITE`) `PENDING`/`FAILED` 상태일 때만 실제로 `PointService.earn()`을 호출해 잔액을 올리고 `RESOLVED`로 표시한다 — 이미 처리된 행이면 조용히 스킵한다(동시 처리 방지).
- 비동기 적용이 예외를 던지면(잔액 행이 없다거나 하는 이례적 상황) 로그만 남기고 `point_earn_log`를 `FAILED`로 표시한다. 이후 배치(`pointEarnRetryJob`)가 `PENDING`/`FAILED` 행을 주기적으로 주워 같은 방식으로 재시도한다 — Slack 알림 재시도(`payment_alert_log` + `paymentAlertRetryJob`, 6절)와 정확히 같은 "비동기 시도 → 실패하면 로그에 남기고 배치가 재시도" 패턴이다.
- 5.1절의 결제 재확인 배치도 똑같이 동작한다: 직접 잔액을 올리지 않고 `point_earn_log`에 `PENDING` 행만 남기고, 실제 적용은 항상 `pointEarnRetryJob` 하나가 담당한다 — 적립을 실제로 반영하는 코드 경로가 시스템 전체에 하나뿐이라 중복 적립 위험이 없다.
- 알림도 같은 방식으로 `markDone()` 커밋 후 `PaymentCompletedEvent` → `PaymentCompletedEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)가 비동기로 처리한다. 다만 알림은 실패해도 로그만 남기고 끝 — 돈이 안 움직이는 작업이라 재시도·보상 대상이 아니다(9절). 포인트 적립은 돈(캐시백)이 걸려 있어서 재시도까지 보장한다는 게 알림과의 차이다.

---

## 5. 실패·불확실 상태의 회수 — 전부 별도 배치가 담당

여기서부터 Core는 스스로 하는 일이 없다. 전부 Jenkins 크론이 트리거할 때만 실행되고 끝나는 1회성 배치다.

### 5.1 결제 재확인

`TIMEOUT_UNKNOWN`으로 남은 결제를 PG에 재조회해서 최종 상태로 수렴시킨다.

1. `payment.status='TIMEOUT_UNKNOWN' AND orders.status='PAYMENT_PENDING' AND payment.updated_at < NOW() - thresholdMinutes`인 건을 청크로 조회.
2. **승인 확인됨(`DONE`)** → `PaymentTimeoutStateService.markPaidIfStillTimeoutUnknown()`이 4절과 똑같은 일을 한다 — 조건부 UPDATE로 `DONE` 전환 → 포인트 사용 확정(잔액 실차감, 원장 기록) → `PAID` 전환 → `point_earn_log`에 `PENDING` 행 기록 → 재고 확정, 전부 같은 MyBatis 트랜잭션 안에서. 실제 포인트 적립 반영은 여기서도 하지 않고 `pointEarnRetryJob`에 맡긴다(4절). **정의된 실패**(`ABORTED`/`EXPIRED`/`CANCELED`) → `FAILED` 전환 → 포인트 예약 해제. **거래 못 찾음/조회 실패** → 로그만 남기고 다음 실행으로 미룸.
3. Core의 즉시 승인 경로(4절)와 이 배치 경로 둘 다 정확히 같은 일(포인트 사용 확정·재고 확정·주문 `PAID`, 포인트 적립은 기록만)을 하는 각자의 트랜잭션으로 처리한다 — 예전엔 이 배치 경로가 재고 확정·포인트 적립을 아예 안 해서(saga_log도 안 만들어서) 영원히 실행 안 되는 게 가장 심각한 한계였는데, 지금은 두 경로가 같은 로직을 한 트랜잭션으로 직접 수행해서 이 갭 자체가 없다.

### 5.2 정산 생성

결제 상태가 `DONE`/`PARTIAL_CANCELED`/`CANCELED`인데 정산 행이 없는 건에 대해 PG·플랫폼 수수료율로 순정산액을 계산해 "이미 있으면 건너뛰기" 조건부 INSERT로 정산 레코드를 만든다(재실행해도 중복 없음).

### 5.3 가맹점별 정산 집계 (별도 잡, `settlementJob`)

`PENDING` 상태의 `settlement`를 가맹점(`vendor_id`) 단위로 합산해서 `vendor_settlement_summary`에 upsert하고, 반영된 원천 `settlement` 행을 `SETTLED`로 확정한다. `startDate`/`endDate`로 집계 기간을 지정한다. `vendor_settlement_summary`는 Core가 관리하는 공유 `business` 스키마가 아니라 **Batch 자신의 배치 메타데이터 DB**에 있다.

### 배치 실행 파라미터 한눈에 보기

| 잡 | 파라미터 |
|---|---|
| 주문 만료 | `chunkSize`(선택: `startDate`, `endDate`) |
| 결제 재확인 | `chunkSize`, `thresholdMinutes` |
| 정산 생성 | `chunkSize` |
| 가맹점 정산 집계 | `chunkSize`, `startDate`, `endDate` |
| Slack 알림 재시도 | `chunkSize` |
| 포인트 적립 재시도 | `chunkSize` |

값 자체는 이 저장소 밖(Jenkins 크론 실행 커맨드)에서 정해진다. 모든 잡이 실행 전에 필수 파라미터 존재·양수 여부를 검증한다.

---

## 6. 결제 상태 변경 이벤트 발행 — 커밋 직후 직접 발행

결제 상태가 바뀔 때마다 Admin 대시보드가 SSE로 그 변화를 보게 하려고 Redis Pub/Sub에 이벤트를 발행한다. `PaymentStatusEventPublisher.publish()`는 그냥 `ApplicationEventPublisher.publishEvent(PaymentStatusChangedEvent)`만 호출하고, `PaymentStatusChangedEventListener`가 `@TransactionalEventListener(AFTER_COMMIT)`로 받아서 Redisson `RTopic`(`StringCodec` 명시)에 발행한다. 트랜잭션이 롤백되면 리스너 자체가 안 불리므로 "커밋된 상태 변경만 이벤트로 나간다"가 자연히 지켜지고, 별도 적재 테이블이나 릴레이 배치가 필요 없다.

실제 Redis 발행은 `@Async("sseTaskExecutor")`로 응답 스레드 밖에서 처리한다 — 실패해도 결제 결과엔 영향이 없으니(이미 커밋 후라 롤백 대상도 아님) Redis가 느려져도 결제 완료 응답이 지연되지 않게 하려는 목적이다. 알림(`PaymentCompletedEventListener`)·포인트 적립(`PointEarnAsyncService`)과 달리 이 executor(`sseTaskExecutor`, `payment/config/PaymentEventAsyncConfig.java`)는 **단일 스레드**(`corePoolSize=1, maxPoolSize=1`)로 만들었다 — 알림·적립은 알림 ID·로그 ID 단위로 서로 독립적이라 여러 워커가 처리해도 순서가 무의미하지만, SSE는 같은 결제 건의 상태가 시간순으로 이어져야 의미가 있는 스트림이라(예: `initiate()`가 같은 트랜잭션 안에서 READY→IN_PROGRESS를 연달아 발행) 멀티스레드 풀에 얹으면 워커 경합으로 발행 순서가 역전될 수 있기 때문이다. 단일 워커면 큐에 들어온 순서대로만 처리되므로 이 역전 위험 없이 응답 지연 제거 효과만 얻는다.

이건 원래 `outbox_event` + `outboxRelayJob`(배치가 주기적으로 `PENDING`/`FAILED` 행을 읽어 릴레이)을 거쳤는데, 이 채널의 유일한 소비자가 "지금 보고 있는 Admin 화면에 실시간으로 보여주기"뿐이라 걷어냈다 — 유실돼도 새로고침하면 DB에서 최신 상태를 그대로 다시 볼 수 있어서 배치가 보장하던 "재시도로 반드시 도착시키기"가 애초에 필요 없었다(반대로 Slack 치명 알림은 그 보장이 필요해서 `payment_alert_log` + `paymentAlertRetryJob`으로 따로 남겨뒀다 — 4절 참고). `outbox_event` 테이블과 `OutboxEvent`/`OutboxEventRepository` 자체는 다른 용도로 쓸 계획이라 남아있다.

**발행 경로는 이제 원래도 둘이다**: 위 경로와 5.1절 재확인 배치가 각각 독립적으로 같은 채널에 발행한다(재확인 배치는 원래도 커밋 직후 바로 발행하는 경로였다 — 이번에 이 절의 경로가 그 방식과 같아진 것). 양쪽 다 `StringCodec`을 써야 같은 채널에서 정상 파싱된다.

---

## 7. 환불 (`POST /api/payments/{orderNumber}/refund`)

1. 멱등키(`refund:orderNumber:quantity:reason:5초 시간창`)로 승인과 같은 캐시-후-해제 패턴을 쓴다 — 캐시된 성공 응답 있으면 반환, 없으면 락을 잡아 처리한 뒤 성공·실패 무관하게 즉시 락을 반납하되 성공한 응답만 캐싱한다. 부분환불을 여러 번 나눠 요청하는 게 정당한 시나리오라 시간창을 둬서 재시도와 새 요청을 구분한다.
2. 결제 행을 비관적 락으로 잠그고, 환불 요청 금액(`orderItem.unitPrice * quantity`)을 **PG 결제분과 포인트 사용분으로 원래 결제 비율에 따라 나눠** 각각의 환불 가능액에서 차감한다(둘 다 0이면 전체취소, 하나라도 남으면 부분취소) — 아래 참고.
3. PG 취소 호출 — **best-effort**: 실패해도 로그만 남기고 로컬 상태(환불 완료)는 그대로 반영한다.
4. 환불 취소 레코드 기록, 환불 상태를 완료로.

주문 상태는 환불이 일어나도 `PAID`로 남는다 — `OrderStatus`에는 환불을 표현하는 값 자체가 없다. 토스페이먼츠 취소 API(`POST /v1/payments/{paymentKey}/cancel`)는 `Idempotency-Key` 헤더를 지원해서, 같은 멱등키로 재시도해도 중복 취소되지 않는다 — 예전 mock-pg 취소 엔드포인트엔 멱등키가 없어서 있던 한계였는데 지금은 해결됐다.

### 환불 시 포인트 복원과 적립 회수

결제 하나는 PG 결제분(`payment.amount`)과 포인트 사용분(`payment.usedPointAmount`)이 섞여 있을 수 있는데, 환불은 상품 수량 단위(`quantity`)로 들어오지 그 결제가 어느 쪽으로 지불됐는지까지는 지정하지 않는다. 그래서 환불 요청 금액을 **원래 결제 시점의 비율 그대로** PG분과 포인트분으로 나눈다: `pointPortion = refundAmount * usedPointAmount / (amount + usedPointAmount)`, `pgPortion = refundAmount - pointPortion`(`Payment.refund()`). 이 비율은 매 환불 요청마다 항상 원래 결제 총액 기준으로 계산되므로(그때그때 남은 잔여 비율이 아니라), 여러 번 나눠 환불해도 각 회차의 배분이 서로 어긋나지 않는다.

- **포인트 사용분 복원(`pointPortion`)**: `PointService.restore()`가 `point_balance`에 즉시 반영하고 `point_ledger`에 `CANCEL_RESTORE` 타입으로 기록한다. 포인트 사용 확정이 원래 동기였던 것과 마찬가지로(4절), 복원도 외부 의존성이 없는 내부 데이터라 굳이 비동기로 미룰 이유가 없어 환불 트랜잭션 안에서 동기로 처리한다.
- **캐시백 적립 회수**: 포인트 적립(캐시백)은 결제 완료 시점에 이미 비동기+배치 재시도 파이프라인(`point_earn_log`, 4절)으로 분리돼 있어서, 환불 시점에 그 적립이 **이미 잔액에 반영됐는지, 아직 대기 중인지**에 따라 처리가 갈린다(`PointEarnReversalService.reverseProportionally()`):
  - `point_earn_log` 행을 잠그고(`findByPaymentIdWithLock`), 같은 비율(`refundAmount / (원래 총액)`)로 회수할 금액을 계산해 그 행의 `reversedAmount`에 누적한다(전체 적립액 `amount`는 절대 덮어쓰지 않고 그대로 둬서, 이후 부분환불이 반복돼도 항상 같은 원래 총액 기준으로 회수 비율을 계산할 수 있게 한다).
  - 이미 `RESOLVED`(적립이 잔액에 반영된 상태)면: `point_balance`에서 즉시 차감하고 `point_ledger`에 `EARN_REVERSAL`(음수 금액)로 기록한다.
  - 아직 `PENDING`/`FAILED`(적립이 잔액에 반영되기 전)면: 잔액은 건드리지 않는다 — 대신 `PointEarnApplyService.apply()`가 이 로그를 나중에 처리할 때 원래 `amount`가 아니라 `amount - reversedAmount`(즉 `remainingAmount()`)만큼만 적립한다. 그래서 아직 지급 안 된 캐시백은 아예 축소된 금액으로만 지급되고, 이미 지급된 캐시백은 별도로 차감된다 — 어느 경우든 이중 지급도 이중 회수도 없다.
  - 같은 `point_earn_log` 행에 대한 락(`PESSIMISTIC_WRITE`)을 `PointEarnApplyService.apply()`(비동기 적립 적용)와 `PointEarnReversalService.reverseProportionally()`(환불 시 회수)가 똑같이 잡기 때문에, 적립이 지금 막 적용되는 중에 환불이 들어오거나 그 반대의 경우에도 두 트랜잭션이 서로를 기다렸다가 순서대로 처리돼 위 두 갈래(RESOLVED/PENDING) 중 하나로 결정론적으로 귀결된다.
  - 배치 재시도 경로(`pointEarnRetryJob`)도 같은 `reversed_amount` 컬럼을 읽어 `amount - reversed_amount`만 적립하도록 맞춰져 있다 — Core의 동기 재시도 경로와 배치 경로 둘 다 같은 규칙을 따르므로 어느 쪽이 최종적으로 적립을 처리하든 결과가 같다.
- `PointLedgerType.CANCEL_RESTORE`/`EARN_REVERSAL`은 이 기능이 들어오기 전까지 정의만 있고 실제로 쓰이는 곳이 없던 값들이었다 — 환불이 포인트를 전혀 건드리지 않았기 때문이다(포인트로 결제한 뒤 환불하면 그 포인트가 그냥 증발하고, 환불된 주문의 캐시백도 회수되지 않는 상태였다). 이 절의 구현으로 그 갭이 닫혔다.

---

## 8. 4대 축 매핑

| 축 | 담당 지점 |
|---|---|
| 동시성 제어 | 재고: `lock:stock:{productId}` Redisson 락(주문 생성/재고 확정/만료 반환 전부 동일 키 공유, `AopForTransaction`이 `REQUIRED`라 호출부 트랜잭션에 합류). 주문/결제: `SELECT ... FOR UPDATE` 행 잠금 |
| 데이터·재고 정합성 | `total - reserved - sold` 단일 계산, 위변조 검증(요청 금액 vs 서버 계산 금액), 만료 lazy 회수(즉시 재고 반환) + 배치 active 회수(방치분) |
| 트랜잭션 처리 | PG 호출은 항상 DB 트랜잭션 밖, 승인 전 `IN_PROGRESS` 선커밋, 결제 완료(포인트 사용 확정·재고 확정·주문 `PAID`)는 하나의 트랜잭션으로 원자적 커밋, 포인트 적립은 같은 트랜잭션엔 기록만 남기고 실제 반영은 비동기+배치로 분리, 상태 변경 이벤트는 `@TransactionalEventListener(AFTER_COMMIT)`로 커밋된 것만 발행 |
| 실패 시 복구 | PG 승인 실패 시 즉시 재조회(`PgClient.inquire()`, 3.2절)로 대부분 그 자리에서 확정 → 그래도 애매하면 `TIMEOUT_UNKNOWN` + 재확인 배치(4절과 동일한 원자적 완료 로직 재사용)가 최종 안전망, `payment_alert_log` + `paymentAlertRetryJob`(Slack 알림 전달 재시도), `point_earn_log` + `pointEarnRetryJob`(포인트 적립 재시도) |

---

## 9. 알려진 한계

- **재고 확정이 진짜 오버셀(버그)로 영원히 실패하면, 한도·자동 보상 없이 무한정 재시도만 한다.** Saga 보상(`SagaCompensationService`)을 걷어내면서 "재시도 소진 시 PG 취소 + 환불"이라는 자동 안전망도 같이 사라졌다 — 재고 예약 시점에 가용 수량을 이미 검증하므로 정상 흐름에선 발생하지 않는다고 보고 의도적으로 안 만들었다. 실제로 발생하면 `alertPgApprovedButPersistFailed` Slack 알림으로 사람이 보고 수동으로 처리해야 한다.
- **결제 승인 재시도는 원 요청이 `DONE`/`FAILED`처럼 확정된 상태로 끝났을 때만 진짜 멱등성(같은 요청 → 같은 응답)이 지켜진다.** 3.1절의 캐싱은 이 둘일 때만 일어난다 — `TIMEOUT_UNKNOWN`은 "결과를 아직 모른다"는 뜻이라 캐싱하면 나중에 배치가 `DONE`/`FAILED`로 확정한 뒤에도 대기 중이던 요청이 낡은 `TIMEOUT_UNKNOWN`을 계속 받게 되므로 일부러 뺐고, `INSUFFICIENT_POINT_BALANCE`/`AUTHORIZED_AMOUNT_MISMATCH`처럼 예외로 던져지는 요청 검증 실패도 캐싱되지 않는다. 이 경우들에서는 대기하던 중복 요청이 원 요청과 같은 응답을 못 받고 그냥 폴링 타임아웃으로 `PaymentErrorCode.PAYMENT_APPROVAL_TIMEOUT`(409)만 받는다.
- **`paymentKey` 소유권을 승인 전에 PG에 되물어 검증하지 않는다.** 3.1절 참고 — `GET /v1/payments/{paymentKey}`(`PgClient.inquire()`) 호출 자체는 이제 존재하지만, 이건 승인 실패 후 결과를 확정 짓는 용도(3.2절)로만 쓰인다. 승인 요청이 들어온 시점에 그 `paymentKey`가 진짜 이 요청자의 것인지 미리 대조하는 로직은 여전히 없다.
- **환불의 PG 취소가 best-effort라, 실제로 실패해도 로컬 환불 상태는 완료 처리된다.** 이 자체를 회수하는 배치는 아직 없다.
- **환불은 `prepare()`(결제 상태·포인트 복원·캐시백 회수 반영)와 `complete()`(`PaymentCancel` 기록, `Refund` 최종 확정) 두 개의 별도 트랜잭션으로 나뉘어 있고, 그 사이에 PG 취소 호출이 끼어 있다.** `prepare()`가 커밋된 뒤 `complete()`가 실패하면(드묾) `Refund`가 `PROCESSING`에 멈춘 채로 남고, 이걸 재시도하는 배치가 없다 — 돈이 걸린 상태 변경(포인트 복원·회수, 환불 가능액 차감)은 이미 `prepare()`에서 원자적으로 끝나 있으므로 정합성이 깨지는 건 아니지만, `Refund`/`PaymentCancel` 레코드의 최종 확정만 수동 개입이 필요할 수 있다.
- **주문 생성 멱등키는 "재사용됐다"만 판단하고 "같은 키인데 내용이 다르다"는 구분하지 않는다.** 요청 내용 불일치를 알려주는 별도 에러(예: 422)는 없다.
- **알림은 보상 대상이 아니다.** 돈이 안 움직여 무한정 방치돼도 무방하다고 보고 재시도·보상 로직을 안 걸었다. (Slack 발송 자체의 재시도는 4절 참고 — `payment_alert_log` + `paymentAlertRetryJob`으로 별도로 보장된다.)
