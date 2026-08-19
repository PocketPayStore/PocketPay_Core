package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

import pocketpaystore.pocketpay_core.point.domain.PointBalance;

public interface PointBalanceRepositoryCustom {

	Optional<PointBalance> findByMemberIdForUpdate(Long memberId);

	Optional<Long> findBalanceByMemberId(Long memberId);

}
