package pocketpaystore.pocketpay_core.pg.service;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_core.pg.domain.PgCallbackLog;
import pocketpaystore.pocketpay_core.pg.repository.PgCallbackLogRepository;

/**
 * 토스페이먼츠는 결제 상태 변경 웹훅(PAYMENT_STATUS_CHANGED)엔 서명을 실어 보내지 않는다 —
 * 서명 헤더(tosspayments-webhook-signature)는 정산 이벤트(payout.changed, seller.changed)에만 붙는다.
 * 그래서 페이로드 내용을 그대로 신뢰해 상태를 반영하지 않고 기록만 남긴다 — 실제 결제 상태 확정은
 * 5.1절 재확인 배치가 PG를 직접 조회해서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgWebhookService {

	private final PgCallbackLogRepository pgCallbackLogRepository;
	private final ObjectMapper objectMapper;

	public void receive(String rawPayload) {
		String paymentKey = extractPaymentKey(rawPayload);
		pgCallbackLogRepository.save(PgCallbackLog.create(paymentKey, rawPayload));
	}

	private String extractPaymentKey(String rawPayload) {
		try {
			JsonNode node = objectMapper.readTree(rawPayload);
			JsonNode dataNode = node.get("data");
			JsonNode paymentKeyNode = dataNode == null ? null : dataNode.get("paymentKey");
			return paymentKeyNode == null ? null : paymentKeyNode.asText();
		} catch (Exception e) {
			log.error("[PgWebhook] payload 파싱 실패, paymentKey 없이 기록", e);
			return null;
		}
	}

}
