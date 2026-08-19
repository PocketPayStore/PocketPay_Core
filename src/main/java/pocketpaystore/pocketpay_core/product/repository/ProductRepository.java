package pocketpaystore.pocketpay_core.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
