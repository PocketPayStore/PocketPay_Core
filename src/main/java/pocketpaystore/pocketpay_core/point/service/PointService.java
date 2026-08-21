package pocketpaystore.pocketpay_core.point.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.domain.PointLedger;
import pocketpaystore.pocketpay_core.point.domain.PointLedgerType;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.point.repository.PointLedgerRepository;

@Service
@RequiredArgsConstructor
public class PointService {

	private final PointBalanceRepository pointBalanceRepository;
	private final PointLedgerRepository pointLedgerRepository;

	@Transactional
	public void earn(Long memberId, Long orderId, Long amount) {
		PointBalance balance = pointBalanceRepository.findByMemberIdWithLock(memberId)
				.orElseThrow(() -> new CustomException(PointErrorCode.POINT_BALANCE_NOT_FOUND));
		Long balanceAfter = balance.adjust(amount);
		PointLedger pointLedger = PointLedger.create(memberId, orderId, PointLedgerType.EARN, amount, balanceAfter);
		pointLedgerRepository.save(pointLedger);
	}

	@Transactional
	public void compensateEarn(Long memberId, Long orderId, Long amount) {
		PointBalance balance = pointBalanceRepository.findByMemberIdWithLock(memberId)
				.orElseThrow(() -> new CustomException(PointErrorCode.POINT_BALANCE_NOT_FOUND));
		Long balanceAfter = balance.adjust(-amount);
		pointLedgerRepository.save(
				PointLedger.create(memberId, orderId, PointLedgerType.CANCEL_RESTORE, -amount, balanceAfter));
	}

	@Transactional
	public void use(Long memberId, Long orderId, Long amount) {
		PointBalance balance = pointBalanceRepository.findByMemberIdWithLock(memberId)
				.orElseThrow(() -> new CustomException(PointErrorCode.INSUFFICIENT_POINT_BALANCE));
		Long balanceAfter = balance.use(amount);
		PointLedger pointLedger = PointLedger.create(memberId, orderId, PointLedgerType.USE, -amount, balanceAfter);
		pointLedgerRepository.save(pointLedger);
	}

	@Transactional
	public void compensateUse(Long memberId, Long orderId, Long amount) {
		PointBalance balance = pointBalanceRepository.findByMemberIdWithLock(memberId)
				.orElseThrow(() -> new CustomException(PointErrorCode.POINT_BALANCE_NOT_FOUND));
		Long balanceAfter = balance.adjust(amount);
		pointLedgerRepository.save(
				PointLedger.create(memberId, orderId, PointLedgerType.CANCEL_RESTORE, amount, balanceAfter));
	}

}
