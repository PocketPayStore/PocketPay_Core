package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.point.domain.PointBalance;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long>, PointBalanceRepositoryCustom {

	Optional<PointBalance> findByMemberId(Long memberId);

}
