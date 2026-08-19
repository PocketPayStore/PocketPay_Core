package pocketpaystore.pocketpay_core.order.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.common.idempotency.IdempotencyKeyGuard;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.service.StockService;

@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String IDEMPOTENCY_NAMESPACE = "order";
	private static final String ORDER_NUMBER_PREFIX = "ORD-";

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;
	private final IdempotencyKeyGuard idempotencyKeyGuard;

	@Transactional
	public OrderResponse createOrder(Long memberId, CreateOrderRequest request, String idempotencyKey) {
		if (!idempotencyKeyGuard.tryAcquire(IDEMPOTENCY_NAMESPACE, idempotencyKey)) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

		Product product = productRepository.findById(request.getProductId())
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		long totalAmount = product.getPrice() * request.getQuantity();

		Order order;
		try {
			Order newOrder = Order.create(generateOrderNumber(), memberId, totalAmount, idempotencyKey);
			order = orderRepository.save(newOrder);
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

		stockService.reserve(request.getProductId(), request.getQuantity());
		order.reserveStock();

		OrderItem orderItem = OrderItem.create(order.getId(), request.getProductId(), request.getQuantity(), product.getPrice());
		orderItemRepository.save(orderItem);

		return OrderResponse.from(order, orderItem);
	}

	private String generateOrderNumber() {
		return ORDER_NUMBER_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
	}

}
