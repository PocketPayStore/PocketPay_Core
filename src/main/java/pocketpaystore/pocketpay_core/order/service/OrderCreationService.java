package pocketpaystore.pocketpay_core.order.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.service.StockService;

@Slf4j
@Service
@RequiredArgsConstructor
class OrderCreationService {

	private static final String ORDER_NUMBER_PREFIX = "ORD-";

	private final ProductRepository productRepository;
	private final StockService stockService;
	private final OrderPersistenceService orderPersistenceService;

	public OrderResponse create(Long memberId, CreateOrderRequest request, String idempotencyKey) {
		Product product = productRepository.findById(request.getProductId())
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		long totalAmount = product.getPrice() * request.getQuantity();

		stockService.reserve(request.getProductId(), request.getQuantity());

		try {
			return orderPersistenceService.persist(generateOrderNumber(), memberId, totalAmount, idempotencyKey,
					request.getProductId(), request.getQuantity(), product.getPrice());
		} catch (DataIntegrityViolationException e) {
			releaseReservedStock(request.getProductId(), request.getQuantity());
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		} catch (RuntimeException e) {
			releaseReservedStock(request.getProductId(), request.getQuantity());
			throw e;
		}
	}

	private void releaseReservedStock(Long productId, int quantity) {
		try {
			stockService.releaseReservation(productId, quantity);
		} catch (Exception releaseEx) {
			log.error("주문 저장 실패 후 재고 보상 실패 - 수동 확인 필요. productId={}, quantity={}", productId, quantity, releaseEx);
		}
	}

	private String generateOrderNumber() {
		return ORDER_NUMBER_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
	}

}
