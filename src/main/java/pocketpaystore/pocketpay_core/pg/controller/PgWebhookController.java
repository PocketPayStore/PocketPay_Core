package pocketpaystore.pocketpay_core.pg.controller;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_core.pg.domain.PgCallbackLog;
import pocketpaystore.pocketpay_core.pg.repository.PgCallbackLogRepository;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class PgWebhookController {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final PgCallbackLogRepository pgCallbackLogRepository;
	private final ObjectMapper objectMapper;

	@Value("${pg.webhook.secret}")
	private String webhookSecret;

	@PostMapping("/pg")
	public ResponseEntity<Void> receive(
			@RequestBody String rawPayload,
			@RequestHeader(value = "X-PG-Signature", required = false) String signature
	) {
		boolean signatureValid = verifySignature(rawPayload, signature);
		if (!signatureValid) {
			log.error("[PgWebhook] 서명 검증 실패, 그래도 기록은 남긴다. signature={}", signature);
		}

		String pgTransactionId = extractPgTransactionId(rawPayload);
		PgCallbackLog callbackLog = PgCallbackLog.create(pgTransactionId, rawPayload, signatureValid);
		pgCallbackLogRepository.save(callbackLog);

		return ResponseEntity.ok().build();
	}

	private boolean verifySignature(String rawPayload, String signature) {
		if (signature == null || signature.isBlank()) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] computed = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
			String computedHex = HexFormat.of().formatHex(computed);
			return computedHex.equalsIgnoreCase(signature);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			log.error("[PgWebhook] 서명 검증 중 오류", e);
			return false;
		}
	}

	private String extractPgTransactionId(String rawPayload) {
		try {
			JsonNode node = objectMapper.readTree(rawPayload);
			JsonNode txIdNode = node.get("pgTransactionId");
			return txIdNode == null ? null : txIdNode.asText();
		} catch (Exception e) {
			log.error("[PgWebhook] payload 파싱 실패, pgTransactionId 없이 기록", e);
			return null;
		}
	}

}
