package pocketpaystore.pocketpay_core.payment.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.common.response.ApiResponse;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentResponse;
import pocketpaystore.pocketpay_core.payment.dto.response.InternalPaymentSummaryResponse;
import pocketpaystore.pocketpay_core.payment.service.InternalPaymentService;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
@Validated
public class InternalPaymentController {
	private final InternalPaymentService internalPaymentService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<InternalPaymentSummaryResponse>>> findPayments(
			@RequestParam(defaultValue = "0") @PositiveOrZero Long lastId,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) String orderNumber,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter) {
		List<InternalPaymentSummaryResponse> payments = internalPaymentService.findPayments(
			lastId, size, status, orderNumber, from, to, updatedAfter);
		return ResponseEntity.ok(ApiResponse.ok(payments));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<InternalPaymentResponse>> findPayment(@PathVariable @Positive Long id) {
		InternalPaymentResponse payment = internalPaymentService.findPayment(id);
		return ResponseEntity.ok(ApiResponse.ok(payment));
	}

}
