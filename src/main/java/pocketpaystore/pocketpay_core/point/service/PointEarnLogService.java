package pocketpaystore.pocketpay_core.point.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.point.domain.PointEarnLog;
import pocketpaystore.pocketpay_core.point.event.model.PointEarnRequestedEvent;
import pocketpaystore.pocketpay_core.point.repository.PointEarnLogRepository;

@Service
@RequiredArgsConstructor
public class PointEarnLogService {

	private final PointEarnLogRepository repository;
	private final ApplicationEventPublisher eventPublisher;

	public void requestEarn(Long memberId, Long orderId, Long paymentId, Long amount) {
		PointEarnLog earnLog = repository.save(PointEarnLog.create(memberId, orderId, paymentId, amount));
		eventPublisher.publishEvent(new PointEarnRequestedEvent(earnLog.getId()));
	}

	@Transactional
	public void markFailed(Long id) {
		repository.findByIdWithLock(id).ifPresent(PointEarnLog::markFailed);
	}

}
