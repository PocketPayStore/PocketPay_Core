package pocketpaystore.pocketpay_core.common.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

	// SpEL로 메서드 파라미터를 참조할 수 있다. 예: "'stock:' + #request.productId"
	String key();

	TimeUnit timeUnit() default TimeUnit.SECONDS;

	long waitTime() default 5L;

	// 트랜잭션이 leaseTime보다 오래 걸리면 락이 먼저 풀려버릴 수 있으니 여유 있게 잡을 것.
	long leaseTime() default 3L;

}
