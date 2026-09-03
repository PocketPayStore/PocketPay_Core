package pocketpaystore.pocketpay_core.common.idempotency;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;

@Component
public class IdempotencyKeyGuard {

	private static final String KEY_PREFIX = "idempotency:";
	private static final String LOCK_PREFIX = KEY_PREFIX + "lock:";
	private static final String RESULT_PREFIX = KEY_PREFIX + "result:";
	private static final String LOCKED = "LOCKED";

	private final StringRedisTemplate redisTemplate;

	@Value("${idempotency.lock-ttl-seconds}")
	private long lockTtlSeconds;

	@Value("${idempotency.result-ttl-seconds}")
	private long resultTtlSeconds;

	@Value("${idempotency.lock-wait-timeout-millis}")
	private long lockWaitTimeoutMillis;

	@Value("${idempotency.lock-poll-interval-millis}")
	private long lockPollIntervalMillis;

	public IdempotencyKeyGuard(
			@Qualifier("idempotencyRedisTemplate") StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public boolean tryAcquire(String namespace, String idempotencyKey) {
		Boolean acquired = redisTemplate.opsForValue()
				.setIfAbsent(lockKey(namespace, idempotencyKey), LOCKED, Duration.ofSeconds(lockTtlSeconds));
		return Boolean.TRUE.equals(acquired);
	}

	public void release(String namespace, String idempotencyKey) {
		redisTemplate.delete(lockKey(namespace, idempotencyKey));
	}

	public String getCachedResult(String namespace, String idempotencyKey) {
		return redisTemplate.opsForValue().get(resultKey(namespace, idempotencyKey));
	}

	public void cacheResult(String namespace, String idempotencyKey, String json) {
		redisTemplate.opsForValue().set(resultKey(namespace, idempotencyKey), json, Duration.ofSeconds(resultTtlSeconds));
	}

	public String waitForCachedResult(String namespace, String idempotencyKey) {
		long deadline = System.currentTimeMillis() + lockWaitTimeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			String json = getCachedResult(namespace, idempotencyKey);
			if (json != null) {
				return json;
			}
			sleep(lockPollIntervalMillis);
		}
		throw new CustomException(CommonErrorCode.REQUEST_TIMEOUT);
	}

	private String lockKey(String namespace, String idempotencyKey) {
		return LOCK_PREFIX + namespace + ":" + idempotencyKey;
	}

	private String resultKey(String namespace, String idempotencyKey) {
		return RESULT_PREFIX + namespace + ":" + idempotencyKey;
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CustomException(CommonErrorCode.REQUEST_TIMEOUT);
		}
	}

}
