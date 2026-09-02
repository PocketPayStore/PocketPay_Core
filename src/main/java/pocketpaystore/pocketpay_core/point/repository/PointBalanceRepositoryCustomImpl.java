package pocketpaystore.pocketpay_core.point.repository;

import static pocketpaystore.pocketpay_core.point.domain.QPointBalance.pointBalance;

import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PointBalanceRepositoryCustomImpl implements PointBalanceRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public Optional<Long> findBalanceByMemberId(Long memberId) {
		Long balance = queryFactory
				.select(pointBalance.balance.subtract(pointBalance.reservedAmount))
				.from(pointBalance)
				.where(pointBalance.memberId.eq(memberId))
				.fetchOne();
		return Optional.ofNullable(balance);
	}

}
