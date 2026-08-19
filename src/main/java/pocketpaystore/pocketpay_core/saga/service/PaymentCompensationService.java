package pocketpaystore.pocketpay_core.saga.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.payment.service.PaymentStateService;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.CancelRequest;
import pocketpaystore.pocketpay_core.point.service.PointService;
import pocketpaystore.pocketpay_core.product.service.StockService;
import pocketpaystore.pocketpay_core.saga.domain.SagaStep;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompensationService {

	private final PointService pointService;
	private final StockService stockService;
	private final PaymentStateService paymentStateService;
	private final PgClient pgClient;
	private final PaymentRepository paymentRepository;
	private final SagaLogService sagaLogService;

	public void compensate(Long paymentId, Long orderId, Long memberId,
							boolean pointUsed, long usedPointAmount, boolean pointEarned, long earnedAmount) {
		Long compensationLogId = sagaLogService.start(orderId, SagaStep.PAYMENT);
		sagaLogService.compensating(compensationLogId);
		try {
			if (pointEarned) {
				pointService.compensateEarn(memberId, orderId, earnedAmount);
			}
			if (pointUsed) {
				pointService.compensateUse(memberId, orderId, usedPointAmount);
			}
			stockService.releaseForOrder(orderId);

			tryCancelPg(paymentId);
			paymentStateService.cancelWithOrder(paymentId, orderId);

			sagaLogService.compensated(compensationLogId);
		} catch (Exception e) {
			log.error("[Saga] 보상 트랜잭션 처리 중 오류: orderId={}", orderId, e);
			sagaLogService.fail(compensationLogId, e.getMessage());
		}
	}

	private void tryCancelPg(Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId).orElse(null);
		if (payment == null || payment.getPgTransactionId() == null) {
			return;
		}
		try {
			pgClient.cancel(new CancelRequest(
					payment.getPgTransactionId(), payment.getAmount(), "SAGA_COMPENSATION", compensationCancelId(paymentId)));
		} catch (Exception e) {
			log.error("[Saga] PG 취소 호출 실패 (best-effort, 로컬 상태는 그대로 반영): paymentId={}", paymentId, e);
		}
	}

	private String compensationCancelId(Long paymentId) {
		return "SAGA_COMPENSATION-" + paymentId;
	}

}
