package pocketpaystore.pocketpay_core.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status IN ('DONE', 'PARTIAL_CANCELED', 'CANCELED')")
	Optional<Payment> findRefundableByOrderIdWithLock(Long orderId);

}
