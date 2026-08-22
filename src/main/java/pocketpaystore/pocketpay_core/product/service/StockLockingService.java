package pocketpaystore.pocketpay_core.product.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.common.lock.DistributedLock;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@Service
@RequiredArgsConstructor
class StockLockingService {

	private final StockRepository stockRepository;

	@DistributedLock(key = "'stock:' + #productId")
	public void reserve(Long productId, int quantity) {
		findStock(productId).reserve(quantity);
	}

	@DistributedLock(key = "'stock:' + #productId")
	public void confirm(Long productId, int quantity) {
		findStock(productId).confirm(quantity);
	}

	@DistributedLock(key = "'stock:' + #productId")
	public void release(Long productId, int quantity) {
		findStock(productId).release(quantity);
	}

	private Stock findStock(Long productId) {
		return stockRepository.findByProductId(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

}
