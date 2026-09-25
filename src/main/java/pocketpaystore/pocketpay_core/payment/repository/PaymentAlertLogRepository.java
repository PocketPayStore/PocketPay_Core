package pocketpaystore.pocketpay_core.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertLog;

public interface PaymentAlertLogRepository extends JpaRepository<PaymentAlertLog, Long> {
}
