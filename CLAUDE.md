# CLAUDE.md — payment-core

포켓몬 카드 스토어의 결제 코어 API 서버다. 고객 화면은 없고 Admin 페이지용 API를 포함한다.

## 포트폴리오 5대 요소

| # | 요소 | 성격 | 핵심 |
|---|---|---|---|
| 1 | 결제 코어 정합성 | 안정성 | 멱등성, 상태 머신, 외부 호출과 트랜잭션 분리, 보상 처리 |
| 2 | Admin 운영 모니터링 | 운영성 | SSE 결제 상태 추적, Slack 이상 알림, 수동 조작 감사 로그 |
| 3a | 재고 차감 동시성 | 성능 | 비관적 락 → Redisson 분산락, TPS·p95·DB 커넥션 비교 |
| 3c | 재고 선점-만료 경합 | 안정성 | 주문 만료와 결제 승인 경쟁을 `orders` 락으로 제어 |
| 4 | PG 장애 격리 | 안정성 | Retry + Circuit Breaker, Tomcat 스레드 고갈 완화 |
| 5 | 결제 재확인·정산 대사 | 안정성 | `IN_PROGRESS`·`TIMEOUT_UNKNOWN` 최종 수렴, PG 데이터 대조 |

3a와 3c를 합치지 않는다. 3a는 성능 실험이고 3c는 정합성 검증이다. 요소 4의 성능 수치는 장애 격리 효과를 보조하는 자료다.

## 기술 스택

- Java 17, Spring Boot 4.1, Spring MVC, Spring Data JPA
- MySQL, Flyway, Redis, Redisson
- OpenFeign, Resilience4j, Micrometer, Prometheus, Grafana
- JWT Access Token, `USER`/`ADMIN`
- k6, Docker, AWS ECS Fargate
- Admin 실시간 전달: `SseEmitter` + Redis Pub/Sub
- 운영 알림: Slack Incoming Webhook
- RabbitMQ와 Outbox는 사용하지 않는다.

MQ는 PG 승인과 DB 저장을 원자적으로 묶지 못한다. 이 문제는 PG 호출 전 `IN_PROGRESS` 저장, PG 멱등키, 재확인 배치로 해결한다.

## 핵심 도메인 규칙

1. 금액은 `Long` 또는 `BigDecimal`만 사용한다.
2. 모든 상태 변경은 상태 머신을 통한다.
3. 주문 생성·결제 승인·환불 요청은 `Idempotency-Key`가 필수다.
4. 멱등성은 Redis `SETNX`와 DB unique constraint로 이중 방어한다.
5. PG 승인 요청에도 동일한 멱등키를 전달한다.
6. PG 호출은 DB 트랜잭션 밖에서 수행한다.
7. PG 호출 전에 결제 의도를 `IN_PROGRESS`로 커밋한다.
8. PG 결과가 불명확하면 `TIMEOUT_UNKNOWN`으로 남기고 요청 스레드에서 재조회하지 않는다.
9. 한 주문에는 한 상품 종류만 담는다.
10. 가용 재고는 `total - reserved - sold`이며 한 도메인 서비스에서만 계산한다.
11. 포인트 잔액은 `point_balance`, 변동 내역은 append-only `point_ledger`에 저장한다.
12. 환불은 `payment` 행을 비관적 락으로 잠그고 `refundable_amount`를 차감한다.
13. 여러 테이블을 한 트랜잭션에서 동시에 비관적 락으로 잠그지 않는다.
14. SSE와 Slack은 결제 상태의 원본이 아니다. DB 커밋 후에만 알린다.
15. Admin 수동 작업은 `admin_audit_log`에 변경 전후 상태를 기록한다.

## 결제 승인 흐름

```text
멱등키 검증
→ 주문·금액 검증
→ payment IN_PROGRESS 저장 및 커밋
→ 트랜잭션 밖에서 PG 승인 호출
→ 성공: payment DONE 저장
→ 실패 확정: payment FAILED 저장
→ 결과 불명: payment TIMEOUT_UNKNOWN 저장
→ 성공 시 포인트·재고·정산 후속 처리
```

PG 승인 후 내부 저장이 실패하면 `IN_PROGRESS`가 남는다. 요소 5의 배치가 PG 거래를 재조회해 복구한다.

## 요소 1 — 결제 코어 정합성

- `PaymentSagaOrchestrator`가 포인트 적립, 재고 확정, 알림, 정산 적재를 순서대로 호출한다.
- 각 단계는 짧은 독립 트랜잭션과 `SagaLog`를 사용한다.
- 포인트·재고 단계 실패 시 성공한 단계를 역순 보상하고 PG 취소를 시도한다.
- PG 취소 실패와 내부 보상 실패는 운영 알림 대상으로 남긴다.
- 결제 성공 응답은 Redis에 TTL과 함께 캐시한다.

필수 검증:

- 동일 멱등키 동시 요청에서 PG 중복 승인 0건
- 불가능한 상태 전이 차단
- 후속 단계 실패 시 보상 결과 일치
- PG 승인 성공 후 DB 저장 실패 시 복구 가능

## 요소 2 — Admin 운영 모니터링

### SSE

- DB 커밋 후 결제 상태 변경 이벤트를 발행한다.
- 이벤트 필드: `eventId`, `paymentId`, `orderNumber`, `previousStatus`, `status`, `occurredAt`
- Redis Pub/Sub으로 여러 ECS 인스턴스에 전달한다.
- Pub/Sub과 SSE 유실은 허용한다. 최초 연결·재연결 시 DB를 다시 조회한다.
- heartbeat와 emitter 정리를 구현한다.

### Slack

알림 대상:

- 재확인 한도를 초과한 결제
- PG 승인 후 내부 저장·보상 실패
- PG/내부 정산 불일치
- Circuit Breaker OPEN

`operational_alert.alert_key`와 cooldown으로 중복 알림을 막는다. Slack 장애는 결제 결과에 영향을 주지 않는다. 민감정보는 전송하지 않는다.

### Admin API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/admin/payments` | 결제 검색 |
| GET | `/api/admin/payments/stream` | 결제 상태 SSE 구독 |
| GET | `/api/admin/alerts` | 미해결 운영 이슈 조회 |
| POST | `/api/admin/payments/{id}/reconcile` | 결제 수동 재확인 |
| GET | `/api/admin/audit-logs` | 수동 작업 감사 로그 조회 |

필수 검증:

- DB 커밋 전 SSE 미발행
- SSE 재접속 후 DB 스냅샷 복구
- 여러 ECS 인스턴스 간 이벤트 전달
- Slack 중복 억제와 전송 실패 격리
- Admin 수동 작업 감사 로그 기록

## 요소 3a — 재고 차감 동시성

Before:

- `SELECT ... FOR UPDATE`로 `stock` 행 직렬화
- 락 대기 중 DB 커넥션 점유

After:

- `lock:stock:{productId}` Redisson 락 사용
- Redis에서 대기한 뒤 짧은 DB 트랜잭션 실행
- `FOR UPDATE` 제거

같은 락을 다음 세 경로에 모두 적용한다.

- 주문 생성: `reserve`
- 결제 성공: `confirmForOrder`
- 실패·만료: `releaseForOrder`

명시적 `leaseTime` 사용 시 Redisson watchdog이 동작하지 않는다. 임계구역이 lease time을 넘지 않게 설정하거나 watchdog 방식으로 변경한다.

실험:

- 서로 다른 상품 요청
- 동일 핫 상품 집중 요청
- TPS, p95, Hikari active/max/pending, 락 대기 측정
- 최종 재고 정합성 검증

## 요소 3c — 재고 선점·만료 경합

- `PAYMENT_PENDING` 주문을 `OrderExpirationScheduler`가 만료한다.
- 연결 결제가 `IN_PROGRESS` 또는 `TIMEOUT_UNKNOWN`이면 만료하지 않는다.
- 결제 결과 재확인은 요소 5가 담당한다.
- 만료와 결제 승인은 같은 `orders` 행을 짧게 비관적 락으로 잠근다.
- 확정된 한 흐름만 상태를 변경하고 다른 흐름은 종료한다.
- 재고 반환은 별도 짧은 트랜잭션으로 처리한다.

필수 검증:

- 동일 주문에 만료와 승인 요청을 동시에 실행
- `PAID`와 `EXPIRED` 중 하나만 반영
- 중복 재고 확정·원복 0건

## 요소 4 — PG 장애 격리

Retry 설정:

- 최초 호출 포함 최대 2회
- 네트워크·TIMEOUT·5xx만 재시도
- 4xx는 재시도하지 않음

Circuit Breaker 설정:

- COUNT_BASED, 최근 10건
- 최소 호출 10건
- 실패율 50%
- OPEN 10초
- HALF_OPEN 시험 호출 3건
- 4xx는 실패율에서 제외

최종 부하 테스트 조건:

- 결제 33 RPS, 상품 조회 10 RPS, PG TIMEOUT, 180초
- Tomcat 최대 스레드 200
- Before/After 모두 Hikari 60, `pending≈0` 확인

결과:

| 상품 API | Before | After | 개선 |
|---|---:|---:|---:|
| p95 | 7.21s | 2.54s | 64.8% 감소 |
| p99 | 7.75s | 3.28s | 57.7% 감소 |
| max | 8.82s | 4.42s | 49.9% 감소 |

Before는 Tomcat 스레드 200개가 지속 포화됐다. After는 초기 순간 포화 후 OPEN 상태에서 스레드가 회수됐다. 결과는 "영향 제거"가 아니라 "장애 전파 완화"로 표현한다.

관찰 지표:

- Circuit Breaker `CLOSED`/`OPEN`/`HALF_OPEN`
- 차단 요청 RPS
- Tomcat busy/max
- Hikari active/max/pending
- 비장애 API p95/p99

## 요소 5 — 결제 재확인·정산 대사

### 결제 재확인

`PaymentReconciliationJob`이 오래된 `IN_PROGRESS`와 `TIMEOUT_UNKNOWN`을 처리한다.

```text
대상 선점 및 커밋
→ 트랜잭션 밖에서 PG 거래 조회
→ 별도 트랜잭션으로 결과 반영
```

- PG 승인: `DONE`·`PAID`로 수렴하고 후속 단계를 멱등 실행
- 거래 없음: 반영 지연을 고려해 횟수·시간 기준 후 `FAILED`
- 조회 실패: 상태 유지 후 다음 실행으로 연기
- 한도 초과: Admin 미해결 목록과 Slack 알림
- 여러 인스턴스가 같은 결제를 중복 처리하지 않게 선점한다.

필요 필드:

- `reconciliation_attempts`
- `last_reconciliation_at`
- `next_reconciliation_at`
- `reconciliation_error`

### 정산 대사

PG 정산 파일과 내부 `payment`·`settlement`을 Chunk 단위로 비교한다.

- 금액 불일치
- 상태 불일치
- 내부에만 존재
- PG에만 존재
- 실패 Chunk부터 재시작
- 불일치는 Admin과 Slack에 중복 없이 노출

필수 검증:

- PG 승인 후 DB 저장 실패를 주입하고 `IN_PROGRESS → DONE` 복구
- `TIMEOUT_UNKNOWN` 최종 수렴
- 배치 재실행 시 후속 처리 중복 0건
- 주입한 대사 불일치 N건 전부 탐지

## 주요 API

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/orders` | 주문 생성 및 재고 예약 |
| POST | `/api/payments` | 결제 승인 |
| GET | `/api/payments/{id}` | 결제 상태 조회 |
| POST | `/api/payments/{id}/cancel` | 전체 취소 |
| POST | `/api/payments/{id}/refund-requests` | 부분환불 요청 |
| POST | `/api/webhooks/pg` | PG 웹훅 수신 |

외부 PG API:

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/mock-pg/approve` | 승인 |
| POST | `/mock-pg/cancel` | 취소·환불 |
| GET | `/mock-pg/transactions/{txId}` | 거래 재확인 |

## 성능·안정성 검증 원칙

- 성능 실험은 최소 3회 반복하고 환경과 데이터량을 기록한다.
- TPS, p50/p95/p99, 에러율, 관련 자원 지표를 함께 본다.
- 정상·경합·장애 시나리오를 분리한다.
- 안정성은 중복 0건, 초과 0건, 단일 상태 확정, 최종 수렴으로 검증한다.
- 개선 수치와 함께 초기 지연, 외부 홉, 운영 복잡성 등 트레이드오프를 기록한다.

## 커맨드

```bash
./gradlew test
./gradlew bootRun
./gradlew flywayMigrate
```
