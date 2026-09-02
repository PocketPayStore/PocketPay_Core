package pocketpaystore.pocketpay_core.product.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;

@Service
@RequiredArgsConstructor
public class StockService {

	private final OrderItemRepository orderItemRepository;
	private final StockLockingService stockLockingService;

	public void reserve(Long productId, int quantity) {
		stockLockingService.reserve(productId, quantity);
	}

	public void releaseReservation(Long productId, int quantity) {
		stockLockingService.release(productId, quantity);
	}

	public void confirmForOrder(Long orderId) {
		OrderItem item = findOrder(orderId);
		stockLockingService.confirm(item.getProductId(), item.getQuantity());
	}

	private OrderItem findOrder(Long orderId) {
		return orderItemRepository.findByOrderId(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.EMPTY_ORDER_ITEMS));
	}

}
