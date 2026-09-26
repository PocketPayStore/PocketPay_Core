package pocketpaystore.pocketpay_core.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import pocketpaystore.pocketpay_core.product.domain.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

	Optional<Stock> findByProductId(Long productId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Stock s WHERE s.productId = :productId")
	Optional<Stock> findByProductIdWithLock(Long productId);

}
