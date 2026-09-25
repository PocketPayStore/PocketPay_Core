package pocketpaystore.pocketpay_core.common.idempotency;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ErrorCode;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyGuard {

	private final StringRedisTemplate redisTemplate;

	@Value("${idempotency.lock-ttl-seconds}")
	private long lockTtlSeconds;

	@Value("${idempotency.result-ttl-seconds}")
	private long resultTtlSeconds;

	@Value("${idempotency.lock-wait-timeout-millis}")
	private long lockWaitTimeoutMillis;

	@Value("${idempotency.lock-poll-interval-millis}")
	private long lockPollIntervalMillis;

	public boolean tryAcquire(String namespace, String idempotencyKey) {
		Boolean acquired = redisTemplate.opsForValue()
				.setIfAbsent(key("lock", namespace, idempotencyKey), "LOCKED", Duration.ofSeconds(lockTtlSeconds));
		return Boolean.TRUE.equals(acquired);
	}

	public void release(String namespace, String idempotencyKey) {
		redisTemplate.delete(key("lock", namespace, idempotencyKey));
	}

	public String getCachedResult(String namespace, String idempotencyKey) {
		return redisTemplate.opsForValue().get(key("result", namespace, idempotencyKey));
	}

	public void cacheResult(String namespace, String idempotencyKey, String json) {
		redisTemplate.opsForValue().set(key("result", namespace, idempotencyKey), json, Duration.ofSeconds(resultTtlSeconds));
	}

	public String waitForCachedResult(String namespace, String idempotencyKey, ErrorCode timeoutErrorCode) {
		long deadline = System.currentTimeMillis() + lockWaitTimeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			String json = getCachedResult(namespace, idempotencyKey);
			if (json != null) {
				return json;
			}
			sleep(lockPollIntervalMillis, timeoutErrorCode);
		}
		throw new CustomException(timeoutErrorCode);
	}

	private String key(String type, String namespace, String idempotencyKey) {
		return "idempotency:" + type + ":" + namespace + ":" + idempotencyKey;
	}

	private void sleep(long millis, ErrorCode timeoutErrorCode) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CustomException(timeoutErrorCode);
		}
	}

}
