package pocketpaystore.pocketpay_core.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * outbox_event 테이블은 Core가 쓰기 전용으로 소유한다({@link pocketpaystore.pocketpay_core.payment.event.publisher.PaymentStatusEventPublisher}).
 * 실제 발행(릴레이)은 PocketPay_Batch의 outboxRelayJob이 같은 테이블을 직접 읽어서 수행한다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
