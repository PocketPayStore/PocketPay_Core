package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long>, PointBalanceRepositoryCustom {

	Optional<PointBalance> findByMemberId(Long memberId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT pb FROM PointBalance pb WHERE pb.memberId = :memberId")
	Optional<PointBalance> findByMemberIdWithLock(Long memberId);

}
