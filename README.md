# PocketPay Core

PocketPay의 주문·결제·환불을 담당하는 핵심 API 서버입니다. 외부 PG 호출과 DB 트랜잭션을 분리하고, 멱등성·분산 락·복구 가능한 후처리로 결제 정합성과 장애 격리를 다룹니다.

## 주요 기능

- JWT 인증, 재고 예약 기반 주문 생성
- 포인트 혼합·전액 결제와 부분·전체 환불
- 토스페이먼츠 실 API 연동 — 승인·취소·거래 조회, Retry와 Circuit Breaker
- 주문·결제·환불 멱등성 보장
- PG 승인 실패 시 즉시 재조회로 우선 확정, 그래도 불명확하면 TIMEOUT_UNKNOWN으로 Batch 대사에 위임
- 포인트 사용 예약·확정, 구매 포인트 적립(비동기+재시도), 환불 시 포인트 복원·적립 회수
- 재고 확정은 결제 완료 트랜잭션에 포함, 포인트 적립·알림은 커밋 후 비동기 후처리로 분리
- 정산 생성은 Batch로 위임
- Redis Pub/Sub 결제 상태 이벤트
- Actuator·Prometheus 모니터링

## 핵심 설계

### 외부 호출과 트랜잭션 분리

결제 처리 중 상태를 짧은 트랜잭션으로 저장한 뒤 트랜잭션 밖에서 PG를 호출합니다. 응답 결과는 별도 트랜잭션에서 완료·실패·확인 필요 상태로 반영해 PG 지연이 DB 자원을 장시간 점유하지 않도록 했습니다.

### 결과를 확정할 수 없는 결제

타임아웃은 실제 승인 실패를 의미하지 않습니다. PG 응답을 못 받으면 그 자리에서 PG를 한 번 더 즉시 조회해 대부분 바로 확정하고(완료/실패), 그마저 불명확하면 그때 TIMEOUT_UNKNOWN으로 저장해 Batch가 PG 거래 결과를 다시 조회해 내부 상태를 보정하는 최종 안전망 역할을 합니다.

### 결제 핵심 상태와 후처리 분리

결제·주문 완료, 예약된 포인트 사용 확정, 재고 확정을 **하나의 트랜잭션**으로 원자적으로 커밋합니다 — 이 중 하나라도 실패하면 전체가 롤백돼 부분 완료 상태가 남지 않습니다. 반대로 구매 포인트 적립과 결제 완료 알림은 "돈의 유효성"과 무관한 부가 작업이라 커밋 이후 비동기 후처리로 분리했고, 실패하면 실패 로그를 남기고 Batch가 재시도합니다 — 부가 작업의 실패가 이미 확정된 결제를 되돌리지 않습니다.

환불도 같은 원칙을 따릅니다. 환불 금액을 원래 결제 시점의 PG·포인트 비율대로 나눠, 포인트 사용분 복원과 결제 가능액 차감을 하나의 트랜잭션에서 처리합니다. 구매 시 적립된 캐시백도 환불 비율만큼 회수하는데, 아직 지급 전이면 지급 예정액을 줄이고 이미 지급됐으면 잔액에서 바로 차감해 이중 지급·이중 회수를 막습니다.

정산 생성은 Core의 결제 응답 경로에서 수행하지 않습니다. Batch가 완료된 결제 중 아직 정산되지 않은 건을 조회해 정산 데이터를 생성하므로 정산 지연이나 실패가 고객 결제 응답에 영향을 주지 않습니다.

~~~mermaid
flowchart LR
    A[주문 생성] --> B[재고 예약]
    B --> C[결제 처리 중 저장]
    C --> D[외부 PG 승인]
    D -->|성공| E["결제·주문 완료(포인트 사용 확정·재고 확정 포함, 단일 트랜잭션)"]
    D -->|거절| F[결제 실패]
    D -->|결과 불명확| G[즉시 재조회]
    G -->|확정 안 됨| H[TIMEOUT_UNKNOWN]
    H --> I[Batch 거래 대사]
    E --> J[포인트 적립·알림 비동기 후처리]
    J -->|실패| K[Batch 재시도]
    E --> L[Batch 정산 생성]
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
| GET | /api/payments/{orderNumber} | 결제 상태 조회 |
| POST | /api/payments/{orderNumber}/refund | 부분·전체 환불 |
| POST | /api/webhooks/pg | PG Webhook 수신 |

## 실행

JDK 17, MySQL, Redis가 필요합니다. PG는 토스페이먼츠 실 API(`https://api.tosspayments.com`)를 호출하므로 별도 Mock 서버는 필요 없고, 테스트용 시크릿 키만 있으면 됩니다. `spring.profiles.active=local`(기본값)이 활성화되므로 `src/main/resources/application-local.yml`이 있어야 부팅됩니다 — 이 파일은 `.gitignore` 대상이라 저장소엔 없습니다. 같은 디렉터리의 `application-local.yml.example`을 `application-local.yml`로 복사한 뒤 DB·Redis 접속 정보와 JWT/토스 시크릿 키를 환경에 맞게 채우세요.

~~~bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# application-local.yml을 열어 값을 채운 뒤 (toss.secret-key 등)
./gradlew bootRun --args='--spring.profiles.active=local'
~~~

## 테스트

~~~bash
./gradlew test
~~~

주문 생성, 결제 승인과 포인트 예약·확정, PG 장애 처리, 환불·재고 동시성, Redis 이벤트와 실패 로그를 테스트합니다.

## 연관 프로젝트
- **[PocketPay Batch](https://github.com/PocketPayStore/PocketPay_Batch)**: 주문 만료, 미확정 결제 대사, 후처리 복구와 정산
- **[PocketPay Admin](https://github.com/PocketPayStore/PocketPay_Admin)**: 결제·정산 조회와 운영 화면
- **[PocketPay PG](https://github.com/PocketPayStore/PocketPay_PG)**: 장애 상황을 제공하던 Mock PG(현재는 토스페이먼츠 실 API로 대체돼 결제 흐름에 더 이상 관여하지 않음)

## 참고 블로그
- **[결제 시스템 참고 블로그](https://velog.io/@rlaehddbs4521/series/PocketPay)**
