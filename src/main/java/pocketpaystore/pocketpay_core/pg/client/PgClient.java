package pocketpaystore.pocketpay_core.pg.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import io.github.resilience4j.retry.annotation.Retry;
import pocketpaystore.pocketpay_core.common.config.FeignConfig;
import pocketpaystore.pocketpay_core.pg.dto.request.ApprovalRequest;
import pocketpaystore.pocketpay_core.pg.dto.request.CancelRequest;
import pocketpaystore.pocketpay_core.pg.dto.response.ApprovalResponse;
import pocketpaystore.pocketpay_core.pg.dto.response.CancelResponse;
import pocketpaystore.pocketpay_core.pg.dto.response.TransactionStatusResponse;

@FeignClient(name = "mock-pg", url = "${mock-pg.base-url}", configuration = FeignConfig.class)
public interface PgClient {

	@Retry(name = "pgClient")
	@PostMapping("/mock-pg/approve")
	ApprovalResponse approve(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ApprovalRequest request);

	@Retry(name = "pgClient")
	@PostMapping("/mock-pg/cancel")
	CancelResponse cancel(@RequestBody CancelRequest request);

	@Retry(name = "pgClient")
	@GetMapping("/mock-pg/transactions/{txId}")
	TransactionStatusResponse getTransaction(@PathVariable("txId") String txId);

}
