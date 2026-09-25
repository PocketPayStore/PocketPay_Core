package pocketpaystore.pocketpay_core.common.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pocketpaystore.pocketpay_core.payment.domain.AlertSeverity;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertType;
import pocketpaystore.pocketpay_core.payment.repository.PaymentAlertLogRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentAlertLogServiceIntegrationTest extends RedisTestContainer {
	@Autowired private PaymentAlertLogService paymentAlertLogService;
	@Autowired private PaymentAlertLogRepository paymentAlertLogRepository;
	@MockitoBean private SlackNotificationService slackNotificationService;

	@Test
	void savesAlertBeforeSendingSlackAfterCommit() {
		paymentAlertLogService.record(PaymentAlertType.PG_APPROVED_PERSIST_FAILED, AlertSeverity.CRITICAL,
				101L, 202L, "PG 승인 후 DB 기록 실패");
		assertThat(paymentAlertLogRepository.count()).isPositive();
		verify(slackNotificationService, timeout(1000)).send(any(), anyString());
	}
}
