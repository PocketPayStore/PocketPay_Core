package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import pocketpaystore.pocketpay_core.point.domain.PointReservation;

public interface PointReservationRepository extends JpaRepository<PointReservation, Long> {

	Optional<PointReservation> findByPaymentId(Long paymentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT pr FROM PointReservation pr WHERE pr.paymentId = :paymentId")
	Optional<PointReservation> findByPaymentIdWithLock(Long paymentId);
}
