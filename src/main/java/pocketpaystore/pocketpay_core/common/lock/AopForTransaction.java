package pocketpaystore.pocketpay_core.common.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// DistributedLockAop의 락 해제(finally)는 이 메서드가 반환된, 즉 트랜잭션이 커밋된 뒤에
// 일어난다 — 커밋 전에 락이 풀리면 다음 락 획득자가 아직 반영 안 된 값을 읽어갈 수 있다.
@Component
public class AopForTransaction {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Object proceed(final ProceedingJoinPoint joinPoint) throws Throwable {
		return joinPoint.proceed();
	}

}
