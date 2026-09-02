package pocketpaystore.pocketpay_core.order.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.common.lock.DistributedLock;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@Service
@RequiredArgsConstructor
class OrderCreationService {

	private static final String ORDER_NUMBER_PREFIX = "ORD-";

	private final ProductRepository productRepository;
	private final StockRepository stockRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;

	@DistributedLock(key = "'stock:' + #request.productId")
	public OrderResponse create(Long memberId, CreateOrderRequest request, String idempotencyKey) {
		Product product = productRepository.findById(request.getProductId())
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		long totalAmount = product.getPrice() * request.getQuantity();

		try {
			return persist(generateOrderNumber(), memberId, totalAmount, idempotencyKey,
					request.getProductId(), request.getQuantity(), product.getPrice());
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}
	}

	private OrderResponse persist(String orderNumber, Long memberId, long totalAmount, String idempotencyKey,
			Long productId, int quantity, Long unitPrice) {
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		stock.reserve(quantity);

		Order order = orderRepository.save(Order.create(orderNumber, memberId, totalAmount, idempotencyKey));
		order.reserveStock();

		OrderItem orderItem = orderItemRepository.save(OrderItem.create(order.getId(), productId, quantity, unitPrice));
		return OrderResponse.from(order, orderItem);
	}

	private String generateOrderNumber() {
		return ORDER_NUMBER_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
	}

}
