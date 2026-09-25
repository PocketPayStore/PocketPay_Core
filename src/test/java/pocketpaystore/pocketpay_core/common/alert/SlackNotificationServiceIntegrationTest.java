package pocketpaystore.pocketpay_core.common.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import pocketpaystore.pocketpay_core.payment.domain.AlertSeverity;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertLog;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertStatus;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertType;
import pocketpaystore.pocketpay_core.payment.repository.PaymentAlertLogRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SlackNotificationServiceIntegrationTest extends RedisTestContainer {

	@Autowired
	private SlackNotificationService slackNotificationService;

	@Autowired
	private PaymentAlertLogRepository paymentAlertLogRepository;

	private HttpServer server;

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	@DisplayName("웹훅 전송이 성공하면 알림을 RESOLVED로 표시한다")
	void send_webhookSucceeds_marksResolved() throws IOException {
		startWebhookServer(200);
		Long alertId = savePendingAlert();

		slackNotificationService.send(alertId, "테스트 메시지");

		PaymentAlertLog alert = awaitStatus(alertId, PaymentAlertStatus.RESOLVED);
		assertThat(alert.getResolvedAt()).isNotNull();
	}

	@Test
	@DisplayName("웹훅 전송이 실패하면 FAILED로 표시하고 retryCount를 늘린다")
	void send_webhookFails_marksFailedAndIncrementsRetryCount() throws IOException {
		startWebhookServer(500);
		Long alertId = savePendingAlert();

		slackNotificationService.send(alertId, "테스트 메시지");

		PaymentAlertLog alert = awaitStatus(alertId, PaymentAlertStatus.FAILED);
		assertThat(alert.getRetryCount()).isEqualTo(1);
	}

	private void startWebhookServer(int statusCode) throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/", exchange -> {
			exchange.sendResponseHeaders(statusCode, -1);
			exchange.close();
		});
		server.start();
		ReflectionTestUtils.setField(slackNotificationService, "webhookUrl",
				"http://localhost:" + server.getAddress().getPort() + "/");
	}

	private Long savePendingAlert() {
		PaymentAlertLog alert = paymentAlertLogRepository.save(
				PaymentAlertLog.create(PaymentAlertType.PG_APPROVED_PERSIST_FAILED, AlertSeverity.CRITICAL, 1L, 2L, "테스트"));
		return alert.getId();
	}

	private PaymentAlertLog awaitStatus(Long alertId, PaymentAlertStatus expected) {
		long deadline = System.currentTimeMillis() + 2000;
		while (System.currentTimeMillis() < deadline) {
			PaymentAlertLog alert = paymentAlertLogRepository.findById(alertId).orElseThrow();
			if (alert.getStatus() == expected) {
				return alert;
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		throw new AssertionError("expected status " + expected + " not reached for alertId=" + alertId);
	}

}
