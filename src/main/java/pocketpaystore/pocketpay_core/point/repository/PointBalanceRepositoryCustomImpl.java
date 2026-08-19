package pocketpaystore.pocketpay_core.point.repository;

import static pocketpaystore.pocketpay_core.point.domain.QPointBalance.pointBalance;

import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;

@RequiredArgsConstructor
public class PointBalanceRepositoryCustomImpl implements PointBalanceRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public Optional<PointBalance> findByMemberIdForUpdate(Long memberId) {
		PointBalance result = queryFactory
				.selectFrom(pointBalance)
				.where(pointBalance.memberId.eq(memberId))
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetchOne();
		return Optional.ofNullable(result);
	}

	@Override
	public Optional<Long> findBalanceByMemberId(Long memberId) {
		Long balance = queryFactory
				.select(pointBalance.balance)
				.from(pointBalance)
				.where(pointBalance.memberId.eq(memberId))
				.fetchOne();
		return Optional.ofNullable(balance);
	}

}
