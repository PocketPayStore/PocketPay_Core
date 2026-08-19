package pocketpaystore.pocketpay_core.order.repository;

import static pocketpaystore.pocketpay_core.order.domain.QOrder.order;

import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.order.domain.Order;

@RequiredArgsConstructor
public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public Optional<Order> findByIdForUpdate(Long orderId) {
		Order result = queryFactory
				.selectFrom(order)
				.where(order.id.eq(orderId))
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetchOne();
		return Optional.ofNullable(result);
	}

}
