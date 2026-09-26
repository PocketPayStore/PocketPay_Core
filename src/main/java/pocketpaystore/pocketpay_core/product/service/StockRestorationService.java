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
public class StockRestorationService {

	private final StockRepository stockRepository;

	@DistributedLock(key = "'stock:' + #productId")
	public void restore(Long productId, int quantity) {
		Stock stock = stockRepository.findByProductIdWithLock(productId)
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
		stock.release(quantity);
	}

}
