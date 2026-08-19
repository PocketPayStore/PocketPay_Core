package pocketpaystore.pocketpay_core.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
class StockPersistenceService {

	private final StockRepository stockRepository;
	private final OrderItemRepository orderItemRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void reserve(Long productId, int quantity) {
		Stock stock = findStock(productId);
		stock.reserve(quantity);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void confirmForOrder(Long orderId) {
		OrderItem item = findOrder(orderId);
		findStock(item.getProductId()).confirm(item.getQuantity());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void releaseForOrder(Long orderId) {
		OrderItem item = findOrder(orderId);
		findStock(item.getProductId()).release(item.getQuantity());
	}

	private OrderItem findOrder(Long orderId) {
		return orderItemRepository.findByOrderId(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.EMPTY_ORDER_ITEMS));
	}

	private Stock findStock(Long productId) {
		return stockRepository.findByProductId(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

}
