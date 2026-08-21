package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

public interface PointBalanceRepositoryCustom {

	Optional<Long> findBalanceByMemberId(Long memberId);

}
