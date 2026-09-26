package pocketpaystore.pocketpay_core.point.event.listener;

import java.util.concurrent.RejectedExecutionException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.point.event.model.PointEarnRequestedEvent;
import pocketpaystore.pocketpay_core.point.service.PointEarnAsyncService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointEarnRequestedEventListener {

	private final PointEarnAsyncService pointEarnAsyncService;

	/**
	 * pointEarnTaskExecutor의 큐·풀이 모두 꽉 차면 executor.execute()가 (호출한 이 스레드에서)
	 * RejectedExecutionException을 즉시 던진다 — applyAsync() 내부까지 도달하지 못하므로 그 안의
	 * markFailed() catch로는 못 잡는다. 여기서 잡지 않아도 point_earn_log는 PENDING으로 남아
	 * Batch의 pointEarnRetryJob이 결국 처리하지만, 원인을 바로 알아볼 수 있도록 명확히 로그를 남긴다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPointEarnRequested(PointEarnRequestedEvent event) {
		try {
			pointEarnAsyncService.applyAsync(event.getPointEarnLogId());
		} catch (RejectedExecutionException e) {
			log.error("[PointEarn] 비동기 스레드풀 포화로 즉시 적립 요청 제출 실패, PENDING으로 남아 배치가 재시도: pointEarnLogId={}",
					event.getPointEarnLogId(), e);
		}
	}

}
