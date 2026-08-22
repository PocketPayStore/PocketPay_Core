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
public class StockService {

	private final StockRepository stockRepository;
	private final OrderItemRepository orderItemRepository;

	// 요소 3a 후속 실험 — 락 없는 사전체크. findByProductId는 FOR UPDATE가 아닌 일반 SELECT라
	// 다른 트랜잭션이 이 행에 비관적 락을 걸고 있어도 기다리지 않고(MVCC 스냅샷 읽기) 즉시
	// 반환된다. 확정 판정이 아니라 "거의 확실히 재고 없는 요청"을 값싸게 걸러내는 용도이며,
	// 최종 판정은 여전히 reserve()의 비관적 락 안에서 이뤄진다. Before(temp/before-redisson-test)
	// 쪽은 이 사전체크가 요청 범위 OSIV 1차 캐시를 오염시켜 오버셀을 냈던 걸 detach()로
	// 우회했는데, 여기(After)는 그 대신 application-dev.yml에서 spring.jpa.open-in-view를
	// false로 꺼서 애초에 이 클래스 저 클래스가 세션을 공유할 일 자체를 없앴다.
	@Transactional(readOnly = true)
	public void checkAvailableWithoutLock(Long productId, int quantity) {
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		if (stock.availableQuantity() < quantity) {
			throw new CustomException(ProductErrorCode.INSUFFICIENT_STOCK);
		}
	}

	@Transactional
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
		return stockRepository.findByProductIdWithLock(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

}
