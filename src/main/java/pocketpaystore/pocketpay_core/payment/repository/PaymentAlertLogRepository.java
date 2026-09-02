package pocketpaystore.pocketpay_core.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertLog;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertStatus;
import java.util.List;

public interface PaymentAlertLogRepository extends JpaRepository<PaymentAlertLog, Long> {
	List<PaymentAlertLog> findTop100ByStatusOrderByIdAsc(PaymentAlertStatus status);
}
