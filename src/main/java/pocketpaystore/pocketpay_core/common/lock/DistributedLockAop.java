package pocketpaystore.pocketpay_core.common.lock;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAop {

	private static final String REDISSON_LOCK_PREFIX = "lock:";

	private final RedissonClient redissonClient;
	private final AopForTransaction aopForTransaction;

	@Value("${lock.default-wait-time-seconds}")
	private long defaultWaitTimeSeconds;

	@Value("${lock.default-lease-time-seconds}")
	private long defaultLeaseTimeSeconds;

	@PostConstruct
	void validateDefaults() {
		if (defaultWaitTimeSeconds <= defaultLeaseTimeSeconds) {
			throw new IllegalStateException(
					"lock.default-wait-time-seconds(%d)는 lock.default-lease-time-seconds(%d)보다 커야 한다"
							.formatted(defaultWaitTimeSeconds, defaultLeaseTimeSeconds));
		}
	}

	@Around("@annotation(pocketpaystore.pocketpay_core.common.lock.DistributedLock)")
	public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

		String key = REDISSON_LOCK_PREFIX
				+ CustomSpringELParser.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), distributedLock.key());
		RLock rLock = redissonClient.getLock(key);

		long waitTime = distributedLock.waitTime() == DistributedLock.UNSET ? defaultWaitTimeSeconds : distributedLock.waitTime();
		long leaseTime = distributedLock.leaseTime() == DistributedLock.UNSET ? defaultLeaseTimeSeconds : distributedLock.leaseTime();

		try {
			boolean acquired = rLock.tryLock(waitTime, leaseTime, distributedLock.timeUnit());
			if (!acquired) {
				log.warn("[DistributedLock] 락 획득 실패: {}", key);
				throw new CustomException(CommonErrorCode.REQUEST_TIMEOUT);
			}

			return aopForTransaction.proceed(joinPoint);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		} finally {
			try {
				rLock.unlock();
			} catch (IllegalMonitorStateException e) {
				log.info("[DistributedLock] 이미 해제된 락: {} {}", method.getName(), key);
			}
		}
	}

}
