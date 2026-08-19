package pocketpaystore.pocketpay_core.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

}
