package pocketpaystore.pocketpay_core.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.auth.resolver.AuthMember;
import pocketpaystore.pocketpay_core.auth.resolver.LoginMember;
import pocketpaystore.pocketpay_core.common.response.ApiResponse;
import pocketpaystore.pocketpay_core.payment.dto.request.ApprovePaymentRequest;
import pocketpaystore.pocketpay_core.payment.dto.request.CreateRefundRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.PaymentResponse;
import pocketpaystore.pocketpay_core.payment.dto.response.RefundResponse;
import pocketpaystore.pocketpay_core.payment.service.PaymentApprovalService;
import pocketpaystore.pocketpay_core.payment.service.PaymentRefundService;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentApprovalService paymentApprovalService;
	private final PaymentRefundService paymentRefundService;

	@PostMapping("/{orderNumber}")
	public ResponseEntity<ApiResponse<PaymentResponse>> approve(
			@AuthMember LoginMember loginMember,
			@PathVariable String orderNumber,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody ApprovePaymentRequest request
	) {
		PaymentResponse response = paymentApprovalService.approve(loginMember.getMemberId(), orderNumber, idempotencyKey, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{orderNumber}/refund")
	public ResponseEntity<ApiResponse<RefundResponse>> refund(
			@AuthMember LoginMember loginMember,
			@PathVariable String orderNumber,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateRefundRequest request
	) {
		RefundResponse response = paymentRefundService.refund(loginMember.getMemberId(), orderNumber, request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}
}
