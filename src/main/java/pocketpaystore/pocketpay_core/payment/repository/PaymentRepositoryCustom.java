package pocketpaystore.pocketpay_core.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentResponse;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentSummaryResponse;

public interface PaymentRepositoryCustom {

	List<InternalPaymentSummaryResponse> findInternalPayments(Long lastId, int size, PaymentStatus status,
			String orderNumber, LocalDateTime from, LocalDateTime to, LocalDateTime updatedAfter);

	Optional<InternalPaymentResponse> findInternalPayment(Long paymentId);

}
