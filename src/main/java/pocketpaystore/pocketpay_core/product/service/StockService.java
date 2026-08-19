package pocketpaystore.pocketpay_core.product.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockService {

	private static final int MAX_ATTEMPTS = 3;

	private final StockPersistenceService stockPersistenceService;

	@Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = MAX_ATTEMPTS,
			backoff = @Backoff(delay = 50, random = true))
	public void reserve(Long productId, int quantity) {
		stockPersistenceService.reserve(productId, quantity);
	}

	@Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = MAX_ATTEMPTS,
			backoff = @Backoff(delay = 50, random = true))
	public void confirmForOrder(Long orderId) {
		stockPersistenceService.confirmForOrder(orderId);
	}

	@Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = MAX_ATTEMPTS,
			backoff = @Backoff(delay = 50, random = true))
	public void releaseForOrder(Long orderId) {
		stockPersistenceService.releaseForOrder(orderId);
	}

}
