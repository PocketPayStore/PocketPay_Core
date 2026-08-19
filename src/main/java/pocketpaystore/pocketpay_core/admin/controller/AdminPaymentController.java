package pocketpaystore.pocketpay_core.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.admin.resolver.AdminMember;
import pocketpaystore.pocketpay_core.auth.resolver.LoginMember;
import pocketpaystore.pocketpay_core.common.response.ApiResponse;
import pocketpaystore.pocketpay_core.payment.dto.response.PaymentResponse;
import pocketpaystore.pocketpay_core.payment.service.PaymentApprovalService;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

	private final PaymentApprovalService paymentApprovalService;

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
			@AdminMember LoginMember admin,
			@PathVariable Long id
	) {
		PaymentResponse payment = paymentApprovalService.getPayment(id);
		return ResponseEntity.ok(ApiResponse.ok(payment));
	}

}
