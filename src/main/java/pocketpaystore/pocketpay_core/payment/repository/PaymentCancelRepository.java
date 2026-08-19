package pocketpaystore.pocketpay_core.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.payment.domain.PaymentCancel;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
}
