package pocketpaystore.pocketpay_core.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentResponse;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentSummaryResponse;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
public class InternalPaymentService {
	private final PaymentRepository paymentRepository;

	@Transactional(readOnly = true)
	public List<InternalPaymentSummaryResponse> findPayments(Long lastId, int size, PaymentStatus status, String orderNumber,
			LocalDateTime from, LocalDateTime to, LocalDateTime updatedAfter) {
		return paymentRepository.findInternalPayments(
				lastId, size, status, orderNumber, from, to, updatedAfter);
	}

	@Transactional(readOnly = true)
	public InternalPaymentResponse findPayment(Long paymentId) {
		return paymentRepository.findInternalPayment(paymentId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
	}
}
