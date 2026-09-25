package pocketpaystore.pocketpay_core.point.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.domain.PointEarnLog;
import pocketpaystore.pocketpay_core.point.domain.PointEarnStatus;
import pocketpaystore.pocketpay_core.point.domain.PointLedger;
import pocketpaystore.pocketpay_core.point.domain.PointLedgerType;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.point.repository.PointEarnLogRepository;
import pocketpaystore.pocketpay_core.point.repository.PointLedgerRepository;

@Service
@RequiredArgsConstructor
public class PointEarnReversalService {

	private final PointEarnLogRepository pointEarnLogRepository;
	private final PointBalanceRepository pointBalanceRepository;
	private final PointLedgerRepository pointLedgerRepository;

	@Transactional
	public void reverseProportionally(Long paymentId, Long orderId, long refundAmount, long originalTotal) {
		if (refundAmount <= 0 || originalTotal <= 0) {
			return;
		}
		PointEarnLog earnLog = pointEarnLogRepository.findByPaymentIdWithLock(paymentId).orElse(null);
		if (earnLog == null) {
			return;
		}
		long reverseAmount = Math.min(
				(earnLog.getAmount() * refundAmount) / originalTotal,
				earnLog.getAmount() - earnLog.getReversedAmount());
		if (reverseAmount <= 0) {
			return;
		}
		earnLog.reverse(reverseAmount);
		if (earnLog.getStatus() == PointEarnStatus.RESOLVED) {
			PointBalance balance = pointBalanceRepository.findByMemberIdWithLock(earnLog.getMemberId())
					.orElseThrow(() -> new CustomException(PointErrorCode.POINT_BALANCE_NOT_FOUND));
			Long balanceAfter = balance.adjust(-reverseAmount);
			pointLedgerRepository.save(PointLedger.create(
					earnLog.getMemberId(), orderId, PointLedgerType.EARN_REVERSAL, -reverseAmount, balanceAfter));
		}
	}

}
