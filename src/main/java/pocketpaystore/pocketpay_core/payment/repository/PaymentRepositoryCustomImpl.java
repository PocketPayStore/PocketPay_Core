package pocketpaystore.pocketpay_core.payment.repository;

import static pocketpaystore.pocketpay_core.order.domain.QOrder.order;
import static pocketpaystore.pocketpay_core.payment.domain.QPayment.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentResponse;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentSummaryResponse;

@RequiredArgsConstructor
public class PaymentRepositoryCustomImpl implements PaymentRepositoryCustom {
	private final JPAQueryFactory queryFactory;

	@Override
	public List<InternalPaymentSummaryResponse> findInternalPayments(Long lastId, int size, PaymentStatus status,
			String orderNumber, LocalDateTime from, LocalDateTime to, LocalDateTime updatedAfter) {
		return queryFactory
				.select(Projections.constructor(InternalPaymentSummaryResponse.class,
						payment.id, order.orderNumber, payment.status, payment.amount, payment.updatedAt))
				.from(payment)
				.join(order).on(order.id.eq(payment.orderId))
				.where(
						payment.id.gt(lastId),
						statusEq(status),
						orderNumberContains(orderNumber),
						createdAtGoe(from),
						createdAtLt(to),
						updatedAtGt(updatedAfter))
				.orderBy(payment.id.asc())
				.limit(size)
				.fetch();
	}

	@Override
	public Optional<InternalPaymentResponse> findInternalPayment(Long paymentId) {
		InternalPaymentResponse response = queryFactory
				.select(Projections.constructor(InternalPaymentResponse.class,
						payment.id, payment.orderId, order.orderNumber, payment.status, payment.amount,
						payment.usedPointAmount, payment.pgTransactionId, payment.failureCode,
						payment.failureMessage, payment.approvedAt, payment.createdAt, payment.updatedAt))
				.from(payment)
				.join(order).on(order.id.eq(payment.orderId))
				.where(payment.id.eq(paymentId))
				.fetchOne();
		return Optional.ofNullable(response);
	}

	private BooleanExpression statusEq(PaymentStatus status) {
		return status == null ? null : payment.status.eq(status);
	}

	private BooleanExpression orderNumberContains(String orderNumber) {
		return orderNumber == null || orderNumber.isBlank() ? null : order.orderNumber.containsIgnoreCase(orderNumber);
	}

	private BooleanExpression createdAtGoe(LocalDateTime from) {
		return from == null ? null : payment.createdAt.goe(from);
	}

	private BooleanExpression createdAtLt(LocalDateTime to) {
		return to == null ? null : payment.createdAt.lt(to);
	}

	private BooleanExpression updatedAtGt(LocalDateTime updatedAfter) {
		return updatedAfter == null ? null : payment.updatedAt.gt(updatedAfter);
	}
}
