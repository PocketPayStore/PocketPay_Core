package pocketpaystore.pocketpay_core.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.dto.response.ProductResponse;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

// 요소 3a 부하테스트 보조 — 단순 조회 프로브. 재고 예약(reserve)과 달리 락·멱등키·쓰기가
// 전혀 없는 순수 SELECT 두 방이라, 평소엔 몇 ms 안에 끝나야 정상이다. 이 응답시간이 핫 아이템
// 경합 중에 튀면, 그건 비즈니스 로직이 아니라 DB 커넥션 풀(HikariCP) 경합 때문이라고 거의
// 확정적으로 볼 수 있다 — bystander_order(주문 생성)보다 신호가 훨씬 깨끗하다.
@Service
@RequiredArgsConstructor
public class ProductQueryService {

	private final ProductRepository productRepository;
	private final StockRepository stockRepository;

	@Transactional(readOnly = true)
	public ProductResponse getProduct(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));

		return ProductResponse.builder()
				.id(product.getId())
				.name(product.getName())
				.price(product.getPrice())
				.availableQuantity(stock.availableQuantity())
				.build();
	}

}
