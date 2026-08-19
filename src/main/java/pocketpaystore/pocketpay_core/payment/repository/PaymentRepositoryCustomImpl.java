package pocketpaystore.pocketpay_core.payment.repository;

import static pocketpaystore.pocketpay_core.payment.domain.QPayment.payment;

import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;

@RequiredArgsConstructor
public class PaymentRepositoryCustomImpl implements PaymentRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public Optional<Payment> findRefundableByOrderIdForUpdate(Long orderId) {
		Payment result = queryFactory
				.selectFrom(payment)
				.where(payment.orderId.eq(orderId)
						.and(payment.status.in(PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED)))
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetchOne();
		return Optional.ofNullable(result);
	}

}
