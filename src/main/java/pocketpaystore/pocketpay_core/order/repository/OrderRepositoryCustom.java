package pocketpaystore.pocketpay_core.order.repository;

import java.util.Optional;

import pocketpaystore.pocketpay_core.order.domain.Order;

public interface OrderRepositoryCustom {

	Optional<Order> findByIdForUpdate(Long orderId);

}
