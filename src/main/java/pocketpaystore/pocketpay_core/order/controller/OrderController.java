package pocketpaystore.pocketpay_core.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.service.OrderService;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@PostMapping
	public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
			@AuthMember LoginMember loginMember,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request
	) {
		OrderResponse response = orderService.createOrder(loginMember.getMemberId(), request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}

}
