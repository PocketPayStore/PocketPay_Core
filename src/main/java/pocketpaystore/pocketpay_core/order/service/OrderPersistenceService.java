package pocketpaystore.pocketpay_core.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
class OrderPersistenceService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;

	@Transactional
	public OrderResponse persist(String orderNumber, Long memberId, long totalAmount, String idempotencyKey,
			Long productId, int quantity, Long unitPrice) {
		Order order = orderRepository.save(Order.create(orderNumber, memberId, totalAmount, idempotencyKey));
		order.reserveStock();

		OrderItem orderItem = orderItemRepository.save(OrderItem.create(order.getId(), productId, quantity, unitPrice));

		return OrderResponse.from(order, orderItem);
	}

}
