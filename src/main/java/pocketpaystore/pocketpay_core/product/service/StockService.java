package pocketpaystore.pocketpay_core.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@Service
@RequiredArgsConstructor
public class StockService {

	private final StockRepository stockRepository;
	private final OrderItemRepository orderItemRepository;
	private final StockLockingService stockLockingService;

	@Transactional(readOnly = true)
	public void checkAvailableWithoutLock(Long productId, int quantity) {
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		if (stock.availableQuantity() < quantity) {
			throw new CustomException(ProductErrorCode.INSUFFICIENT_STOCK);
		}
	}

	public void reserve(Long productId, int quantity) {
		stockLockingService.reserve(productId, quantity);
	}

	public void confirmForOrder(Long orderId) {
		OrderItem item = findOrder(orderId);
		stockLockingService.confirm(item.getProductId(), item.getQuantity());
	}

	public void releaseForOrder(Long orderId) {
		OrderItem item = findOrder(orderId);
		stockLockingService.release(item.getProductId(), item.getQuantity());
	}

	private OrderItem findOrder(Long orderId) {
		return orderItemRepository.findByOrderId(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.EMPTY_ORDER_ITEMS));
	}

}
