package pocketpaystore.pocketpay_core.order.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.idempotency.IdempotencyKeyGuard;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;

@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String IDEMPOTENCY_NAMESPACE = "order";

	private final OrderCreationService orderCreationService;
	private final IdempotencyKeyGuard idempotencyKeyGuard;

	public OrderResponse createOrder(Long memberId, CreateOrderRequest request, String idempotencyKey) {
		if (!idempotencyKeyGuard.tryAcquire(IDEMPOTENCY_NAMESPACE, idempotencyKey)) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

		return orderCreationService.create(memberId, request, idempotencyKey);
	}

}
