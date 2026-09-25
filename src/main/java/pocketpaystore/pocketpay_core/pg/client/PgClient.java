package pocketpaystore.pocketpay_core.pg.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import pocketpaystore.pocketpay_core.common.config.TossFeignConfig;
import pocketpaystore.pocketpay_core.pg.dto.request.ApprovalRequest;
import pocketpaystore.pocketpay_core.pg.dto.request.CancelRequest;
import pocketpaystore.pocketpay_core.pg.dto.response.ApprovalResponse;
import pocketpaystore.pocketpay_core.pg.dto.response.CancelResponse;

@FeignClient(name = "toss-payments", url = "${toss.base-url}", configuration = TossFeignConfig.class)
public interface PgClient {

	@Retry(name = "pgClient")
	@CircuitBreaker(name = "pgClient")
	@PostMapping("/v1/payments/confirm")
	ApprovalResponse approve(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ApprovalRequest request);

	@Retry(name = "pgClient")
	@CircuitBreaker(name = "pgClient")
	@PostMapping("/v1/payments/{paymentKey}/cancel")
	CancelResponse cancel(@PathVariable("paymentKey") String paymentKey,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody CancelRequest request);

	@Retry(name = "pgClient")
	@CircuitBreaker(name = "pgClient")
	@GetMapping("/v1/payments/{paymentKey}")
	ApprovalResponse inquire(@PathVariable("paymentKey") String paymentKey);

}
