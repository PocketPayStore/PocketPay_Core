package pocketpaystore.pocketpay_core.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.payment.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

	Optional<Refund> findByIdempotencyKey(String idempotencyKey);

}
