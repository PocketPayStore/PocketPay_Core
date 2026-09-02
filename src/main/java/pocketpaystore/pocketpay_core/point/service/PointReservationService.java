package pocketpaystore.pocketpay_core.point.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.domain.PointLedger;
import pocketpaystore.pocketpay_core.point.domain.PointLedgerType;
import pocketpaystore.pocketpay_core.point.domain.PointReservation;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.point.repository.PointLedgerRepository;
import pocketpaystore.pocketpay_core.point.repository.PointReservationRepository;

@Service
@RequiredArgsConstructor
public class PointReservationService {

	private final PointBalanceRepository pointBalanceRepository;
	private final PointReservationRepository pointReservationRepository;
	private final PointLedgerRepository pointLedgerRepository;

	public void reserve(Long paymentId, Long memberId, Long amount) {
		if (amount == 0) {
			return;
		}
		PointBalance balance = findBalanceForUpdate(memberId);
		balance.reserve(amount);
		pointReservationRepository.save(PointReservation.reserve(paymentId, memberId, amount));
	}

	public void confirm(Long paymentId, Long orderId) {
		PointReservation reservation = findReservationForUpdate(paymentId);
		if (!reservation.isReserved()) {
			return;
		}
		PointBalance balance = findBalanceForUpdate(reservation.getMemberId());
		Long balanceAfter = balance.confirmReservation(reservation.getAmount());
		reservation.markUsed();
		pointLedgerRepository.save(PointLedger.create(
				reservation.getMemberId(), orderId, PointLedgerType.USE, -reservation.getAmount(), balanceAfter));
	}

	public void release(Long paymentId) {
		PointReservation reservation = findReservationForUpdate(paymentId);
		if (!reservation.isReserved()) {
			return;
		}
		PointBalance balance = findBalanceForUpdate(reservation.getMemberId());
		balance.releaseReservation(reservation.getAmount());
		reservation.markReleased();
	}

	private PointBalance findBalanceForUpdate(Long memberId) {
		return pointBalanceRepository.findByMemberIdWithLock(memberId)
				.orElseThrow(() -> new CustomException(PointErrorCode.POINT_BALANCE_NOT_FOUND));
	}

	private PointReservation findReservationForUpdate(Long paymentId) {
		return pointReservationRepository.findByPaymentIdWithLock(paymentId)
				.orElseThrow(() -> new CustomException(PointErrorCode.POINT_RESERVATION_NOT_FOUND));
	}
}
