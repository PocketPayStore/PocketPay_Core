package pocketpaystore.pocketpay_core.payment.repository;

import java.util.Optional;

import pocketpaystore.pocketpay_core.payment.domain.Payment;

public interface PaymentRepositoryCustom {

	Optional<Payment> findRefundableByOrderIdForUpdate(Long orderId);

}
