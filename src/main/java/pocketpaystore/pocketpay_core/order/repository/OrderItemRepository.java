package pocketpaystore.pocketpay_core.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.order.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	Optional<OrderItem> findByOrderId(Long orderId);

}
