package pocketpaystore.pocketpay_core.product.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;

/** 재고 예약은 이 서비스를 거치지 않는다 — {@code OrderCreationService}가 주문 저장과 같은 트랜잭션 안에서 직접 처리한다. */
@Service
@RequiredArgsConstructor
public class StockService {

	private final OrderItemRepository orderItemRepository;
	private final StockLockingService stockLockingService;

	public void confirmForOrder(Long orderId) {
		OrderItem item = findOrder(orderId);
		stockLockingService.confirm(item.getProductId(), item.getQuantity());
	}

	private OrderItem findOrder(Long orderId) {
		return orderItemRepository.findByOrderId(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.EMPTY_ORDER_ITEMS));
	}

}
