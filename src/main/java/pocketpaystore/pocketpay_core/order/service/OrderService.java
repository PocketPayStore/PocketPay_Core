package pocketpaystore.pocketpay_core.order.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.idempotency.IdempotencyKeyGuard;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.product.service.StockService;

@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String IDEMPOTENCY_NAMESPACE = "order";

	private final OrderCreationService orderCreationService;
	private final IdempotencyKeyGuard idempotencyKeyGuard;
	private final StockService stockService;

	public OrderResponse createOrder(Long memberId, CreateOrderRequest request, String idempotencyKey) {
		if (!idempotencyKeyGuard.tryAcquire(IDEMPOTENCY_NAMESPACE, idempotencyKey)) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

		// 요소 3a 후속 실험 — 락 없는 사전체크. 재고가 뻔히 없는 요청은 여기서 즉시 걸러져서
		// Redis 분산락(@DistributedLock)조차 시도하지 않는다. 확정 판정이 아니다 — 통과해도
		// 실제 예약 시점엔 이미 소진돼 있을 수 있고, 그건 OrderCreationService.create() 안의
		// 비관적 락에서 최종적으로 다시 걸러진다.
		stockService.checkAvailableWithoutLock(request.getProductId(), request.getQuantity());

		return orderCreationService.create(memberId, request, idempotencyKey);
	}

}
