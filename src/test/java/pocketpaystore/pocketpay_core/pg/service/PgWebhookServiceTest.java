package pocketpaystore.pocketpay_core.pg.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pocketpaystore.pocketpay_core.pg.domain.PgCallbackLog;
import pocketpaystore.pocketpay_core.pg.repository.PgCallbackLogRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PgWebhookServiceTest extends RedisTestContainer {

	@Autowired
	private PgWebhookService pgWebhookService;

	@Autowired
	private PgCallbackLogRepository pgCallbackLogRepository;

	@Test
	@DisplayName("PAYMENT_STATUS_CHANGED 페이로드를 받으면 data.paymentKey를 뽑아 기록을 남긴다")
	void receive_paymentStatusChanged_recordsPaymentKey() {
		String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"createdAt\":\"2026-01-01T00:00:00.000\","
				+ "\"data\":{\"paymentKey\":\"PAY-TOSS-1\",\"orderId\":\"ORD-1\",\"status\":\"DONE\"}}";
		long countBefore = pgCallbackLogRepository.count();

		pgWebhookService.receive(payload);

		assertThat(pgCallbackLogRepository.count()).isEqualTo(countBefore + 1);
		PgCallbackLog saved = lastSaved();
		assertThat(saved.getPgTransactionId()).isEqualTo("PAY-TOSS-1");
		assertThat(saved.getPayload()).contains("PAY-TOSS-1");
	}

	@Test
	@DisplayName("파싱할 수 없는 페이로드가 와도 예외 없이 paymentKey 없는 기록을 남긴다")
	void receive_malformedPayload_recordsWithoutPaymentKey() {
		long countBefore = pgCallbackLogRepository.count();

		pgWebhookService.receive("not-json");

		assertThat(pgCallbackLogRepository.count()).isEqualTo(countBefore + 1);
		assertThat(lastSaved().getPgTransactionId()).isNull();
	}

	private PgCallbackLog lastSaved() {
		return pgCallbackLogRepository.findAll().stream()
				.max((a, b) -> Long.compare(a.getId(), b.getId()))
				.orElseThrow();
	}

}
