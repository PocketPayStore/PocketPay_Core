package pocketpaystore.pocketpay_core.point.event.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.point.event.model.PointEarnRequestedEvent;
import pocketpaystore.pocketpay_core.point.service.PointEarnAsyncService;

@Component
@RequiredArgsConstructor
public class PointEarnRequestedEventListener {

	private final PointEarnAsyncService pointEarnAsyncService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPointEarnRequested(PointEarnRequestedEvent event) {
		pointEarnAsyncService.applyAsync(event.getPointEarnLogId());
	}

}
