package pocketpaystore.pocketpay_core.pg.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.pg.service.PgWebhookService;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class PgWebhookController {

	private final PgWebhookService pgWebhookService;

	@PostMapping("/pg")
	public ResponseEntity<Void> receive(
			@RequestBody String rawPayload,
			@RequestHeader(value = "X-PG-Signature", required = false) String signature
	) {
		pgWebhookService.receive(rawPayload, signature);
		return ResponseEntity.ok().build();
	}

}
