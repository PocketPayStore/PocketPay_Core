package pocketpaystore.pocketpay_core.point.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.point.domain.PointEarnLog;
import pocketpaystore.pocketpay_core.point.repository.PointEarnLogRepository;

@Service
@RequiredArgsConstructor
public class PointEarnApplyService {

	private final PointEarnLogRepository repository;
	private final PointService pointService;

	@Transactional
	public boolean apply(Long id) {
		PointEarnLog earnLog = repository.findByIdWithLock(id)
				.orElseThrow(() -> new CustomException(PointErrorCode.POINT_EARN_LOG_NOT_FOUND));
		if (!earnLog.isRetryable()) {
			return false;
		}
		earnLog.markProcessing();
		long remaining = earnLog.remainingAmount();
		if (remaining > 0) {
			pointService.earn(earnLog.getMemberId(), earnLog.getOrderId(), remaining);
		}
		earnLog.markResolved();
		return true;
	}

}
