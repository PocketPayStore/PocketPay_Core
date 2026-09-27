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
