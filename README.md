# PocketPay Core

PocketPay의 주문·결제·환불을 담당하는 핵심 API 서버입니다. 외부 PG 호출과 DB 트랜잭션을 분리하고, 멱등성·분산 락·복구 가능한 후처리로 결제 정합성과 장애 격리를 다룹니다.

## 주요 기능

- JWT 인증, 재고 예약 기반 주문 생성
- 포인트 혼합·전액 결제와 부분·전체 환불
- PG 승인·취소·거래 조회, Retry와 Circuit Breaker
- 주문·결제·환불 멱등성 보장
- PG 타임아웃 결제의 TIMEOUT_UNKNOWN 관리
- 포인트 사용 예약·확정과 구매 포인트 적립
- 예약 재고 확정 및 결제 알림 후처리
- 정산 생성은 Batch로 위임
- Redis Pub/Sub 결제 상태 이벤트
- Actuator·Prometheus 모니터링

## 핵심 설계

### 외부 호출과 트랜잭션 분리

결제 처리 중 상태를 짧은 트랜잭션으로 저장한 뒤 트랜잭션 밖에서 PG를 호출합니다. 응답 결과는 별도 트랜잭션에서 완료·실패·확인 필요 상태로 반영해 PG 지연이 DB 자원을 장시간 점유하지 않도록 했습니다.

### 결과를 확정할 수 없는 결제

타임아웃은 실제 승인 실패를 의미하지 않습니다. 응답을 받지 못한 결제는 TIMEOUT_UNKNOWN으로 저장하고 Batch가 PG 거래 결과를 다시 조회해 내부 상태를 보정합니다.

### 결제 핵심 상태와 후처리 분리

결제와 주문을 먼저 완료하는 트랜잭션에서 예약된 포인트 사용을 확정합니다. 구매 포인트 적립, 예약 재고 확정, 알림은 결제 응답과 분리된 후처리로 실행하며, 일부 단계가 실패해도 승인 결제를 되돌리지 않고 실패 내역을 남깁니다.

정산 생성은 Core의 결제 응답 경로에서 수행하지 않습니다. Batch가 완료된 결제 중 아직 정산되지 않은 건을 조회해 정산 데이터를 생성하므로 정산 지연이나 실패가 고객 결제 응답에 영향을 주지 않습니다.

~~~mermaid
flowchart LR
    A[주문 생성] --> B[재고 예약]
    B --> C[결제 처리 중 저장]
    C --> D[외부 PG 승인]
    D -->|성공| E[결제·주문 완료]
    D -->|거절| F[결제 실패]
    D -->|결과 불명확| G[결제 결과 확인 필요]
    G --> H[Batch 거래 대사]
    E --> I[포인트 적립·재고 확정·알림]
    I -->|일부 실패| J[영역별 복구 Batch]
    E --> K[Batch 정산 생성]
~~~

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language / Framework | Java 17, Spring Boot 4.1, Spring MVC |
| Persistence | Spring Data JPA, QueryDSL, MySQL, Flyway |
| Cache / Lock | Redis, Redisson |
| External API | Spring Cloud OpenFeign |
| Resilience | Resilience4j Retry, Circuit Breaker |
| Auth / Monitoring | JWT, Actuator, Micrometer, Prometheus |
| Test | JUnit 5, Testcontainers |

## 주요 API

| Method | Endpoint | 설명 |
|---|---|---|
| POST | /api/auth/login | 로그인 및 JWT 발급 |
| POST | /api/orders | 주문 생성과 재고 예약 |
| POST | /api/payments/{orderNumber} | 결제 승인 |
| POST | /api/payments/{orderNumber}/refund | 부분·전체 환불 |
| POST | /api/webhooks/pg | PG Webhook 수신 |

## 실행

JDK 17, MySQL, Redis와 PocketPay PG가 필요합니다. 로컬 설정의 DB·Redis 접속 정보와 JWT/PG 비밀키를 환경에 맞게 설정하세요.

~~~bash
./gradlew bootRun --args='--spring.profiles.active=local'
~~~

기본 Mock PG 주소는 http://localhost:8081입니다.

## 테스트

~~~bash
./gradlew test
~~~

주문 생성, 결제 승인과 포인트 예약·확정, PG 장애 처리, 환불·재고 동시성, Redis 이벤트와 실패 로그를 테스트합니다.

## 연관 프로젝트

- **PocketPay PG**: 장애 상황을 제공하는 Mock PG
- **PocketPay Batch**: 주문 만료, 미확정 결제 대사, 후처리 복구와 정산
- **PocketPay Admin**: 결제·정산 조회와 운영 화면
