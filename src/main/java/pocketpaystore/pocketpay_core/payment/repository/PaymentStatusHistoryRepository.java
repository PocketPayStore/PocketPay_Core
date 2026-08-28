package pocketpaystore.pocketpay_core.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.payment.domain.PaymentStatusHistory;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
}
