package pocketpaystore.pocketpay_core.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StockServiceConcurrencyTest {

	private static final int CONCURRENT_REQUESTS = 10;

	@Autowired
	private StockService stockService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@Test
	@DisplayName("동일 상품에 대한 재고 예약 요청이 동시에 들어와도 비관적 락으로 순차 처리되어 전부 정확히 반영된다")
	void concurrentReserve_noLostUpdates() throws InterruptedException {
		Vendor vendor = vendorRepository.save(Vendor.builder().name("테스트 업체").build());
		Product product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("동시성 테스트 카드").price(1000L).build());
		stockRepository.save(Stock.builder().productId(product.getId())
				.totalQuantity(1000).reservedQuantity(0).soldQuantity(0).build());

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger unexpectedFailureCount = new AtomicInteger();

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					stockService.reserve(product.getId(), 1);
					successCount.incrementAndGet();
				} catch (Exception e) {
					unexpectedFailureCount.incrementAndGet();
				} finally {
					doneLatch.countDown();
				}
			});
		}
		startLatch.countDown();
		boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(finished).isTrue();
		assertThat(unexpectedFailureCount.get()).isZero();
		assertThat(successCount.get()).isEqualTo(CONCURRENT_REQUESTS);

		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(CONCURRENT_REQUESTS);
	}

}
