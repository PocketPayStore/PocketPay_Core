package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import pocketpaystore.pocketpay_core.point.domain.PointEarnLog;

public interface PointEarnLogRepository extends JpaRepository<PointEarnLog, Long> {

	Optional<PointEarnLog> findByPaymentId(Long paymentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PointEarnLog p WHERE p.id = :id")
	Optional<PointEarnLog> findByIdWithLock(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PointEarnLog p WHERE p.paymentId = :paymentId")
	Optional<PointEarnLog> findByPaymentIdWithLock(Long paymentId);

}
