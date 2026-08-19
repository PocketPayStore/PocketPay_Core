package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CriticalAlertService {

	public void alertPgApprovedButPersistFailed(Long orderId, Long paymentId, String pgTransactionId,
												  Long amount, Throwable cause) {
		log.error("[CriticalAlert] PG 승인 성공, DB 기록 실패 — 수동 대사 필요: orderId={}, paymentId={}, pgTransactionId={}, amount={}",
				orderId, paymentId, pgTransactionId, amount, cause);
	}

}
