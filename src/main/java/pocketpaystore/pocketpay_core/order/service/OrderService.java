package pocketpaystore.pocketpay_core.order.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.common.idempotency.IdempotencyKeyGuard;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String IDEMPOTENCY_NAMESPACE = "order";

	private final OrderCreationService orderCreationService;
	private final IdempotencyKeyGuard idempotencyKeyGuard;
	private final StockRepository stockRepository;
	private final ObjectMapper objectMapper;

	public OrderResponse createOrder(Long memberId, CreateOrderRequest request, String idempotencyKey) {
		String namespace = IDEMPOTENCY_NAMESPACE + ":" + memberId;

		OrderResponse cached = readCachedResult(namespace, idempotencyKey);
		if (cached != null) {
			return cached;
		}

		if (!idempotencyKeyGuard.tryAcquire(namespace, idempotencyKey)) {
			OrderResponse justCompleted = readCachedResult(namespace, idempotencyKey);
			if (justCompleted != null) {
				return justCompleted;
			}
			throw new CustomException(OrderErrorCode.ORDER_CREATION_IN_PROGRESS);
		}

		try {
			preCheckStockAvailability(request);
			OrderResponse response = orderCreationService.create(memberId, request, idempotencyKey);
			cacheResult(namespace, idempotencyKey, response);
			return response;
		} finally {
			idempotencyKeyGuard.release(namespace, idempotencyKey);
		}
	}

	private void preCheckStockAvailability(CreateOrderRequest request) {
		Stock stock = stockRepository.findByProductId(request.getProductId())
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		if (stock.availableQuantity() < request.getQuantity()) {
			throw new CustomException(ProductErrorCode.INSUFFICIENT_STOCK);
		}
	}

	private OrderResponse readCachedResult(String namespace, String idempotencyKey) {
		String json = idempotencyKeyGuard.getCachedResult(namespace, idempotencyKey);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, OrderResponse.class);
		} catch (Exception e) {
			log.error("[Order] 캐시된 응답 역직렬화 실패, 캐시 무시: idempotencyKey={}", idempotencyKey, e);
			return null;
		}
	}

	private void cacheResult(String namespace, String idempotencyKey, OrderResponse response) {
		try {
			idempotencyKeyGuard.cacheResult(namespace, idempotencyKey, objectMapper.writeValueAsString(response));
		} catch (Exception e) {
			log.error("[Order] 응답 캐싱 실패: idempotencyKey={}", idempotencyKey, e);
		}
	}

}
