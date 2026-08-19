package pocketpaystore.pocketpay_core.saga.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.saga.domain.SagaLog;
import pocketpaystore.pocketpay_core.saga.domain.SagaStep;
import pocketpaystore.pocketpay_core.saga.repository.SagaLogRepository;

@Service
@RequiredArgsConstructor
public class SagaLogService {

	private final SagaLogRepository sagaLogRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Long start(Long orderId, SagaStep step) {
		SagaLog sagaLog = SagaLog.create(orderId, step);
		return sagaLogRepository.save(sagaLog).getId();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void success(Long sagaLogId) {
		sagaLogRepository.findById(sagaLogId).ifPresent(SagaLog::success);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(Long sagaLogId, String errorMessage) {
		sagaLogRepository.findById(sagaLogId).ifPresent(log -> log.fail(errorMessage));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void compensating(Long sagaLogId) {
		sagaLogRepository.findById(sagaLogId).ifPresent(SagaLog::compensating);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void compensated(Long sagaLogId) {
		sagaLogRepository.findById(sagaLogId).ifPresent(SagaLog::compensated);
	}

}
