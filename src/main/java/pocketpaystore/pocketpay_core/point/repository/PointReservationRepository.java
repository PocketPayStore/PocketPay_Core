package pocketpaystore.pocketpay_core.point.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.point.domain.PointReservation;

public interface PointReservationRepository extends JpaRepository<PointReservation, Long> {

	Optional<PointReservation> findByPaymentId(Long paymentId);
}
