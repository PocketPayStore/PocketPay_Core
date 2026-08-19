package pocketpaystore.pocketpay_core.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.product.domain.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

	Optional<Stock> findByProductId(Long productId);

}
