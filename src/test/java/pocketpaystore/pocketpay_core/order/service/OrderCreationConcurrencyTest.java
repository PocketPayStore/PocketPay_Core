package pocketpaystore.pocketpay_core.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderCreationConcurrencyTest extends RedisTestContainer {

	private static final int TOTAL_STOCK = 5;
	private static final int CONCURRENT_REQUESTS = 10;

	@Autowired
	private OrderService orderService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@Test
	@DisplayName("동일 상품에 동시에 들어온 주문 생성 요청은 가용 재고만큼만 성공하고 나머지는 재고 부족으로 거절된다")
	void concurrentOrderCreation_noOversell() throws InterruptedException {
		Member buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Vendor vendor = vendorRepository.save(Vendor.builder().name("테스트 업체").build());
		Product product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("동시성 테스트 카드").price(1000L).build());
		stockRepository.save(Stock.builder().productId(product.getId())
				.totalQuantity(TOTAL_STOCK).reservedQuantity(0).soldQuantity(0).build());

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger insufficientStockCount = new AtomicInteger();
		AtomicInteger unexpectedFailureCount = new AtomicInteger();

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					CreateOrderRequest request = new CreateOrderRequest(product.getId(), 1);
					orderService.createOrder(buyer.getId(), request, java.util.UUID.randomUUID().toString());
					successCount.incrementAndGet();
				} catch (CustomException e) {
					if (e.getErrorCode() == ProductErrorCode.INSUFFICIENT_STOCK) {
						insufficientStockCount.incrementAndGet();
					} else {
						unexpectedFailureCount.incrementAndGet();
					}
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
		assertThat(successCount.get()).isEqualTo(TOTAL_STOCK);
		assertThat(insufficientStockCount.get()).isEqualTo(CONCURRENT_REQUESTS - TOTAL_STOCK);

		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(TOTAL_STOCK);
		assertThat(stock.availableQuantity()).isZero();
	}

	@Test
	@DisplayName("같은 Idempotency-Key로 동시에 여러 번 요청해도 주문은 하나만 생성되고, 나머지는 같은 응답을 받거나 처리 중(409) 응답을 받는다")
	void concurrentOrderCreation_sameKey_onlyOneOrderCreated() throws InterruptedException {
		Member buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Vendor vendor = vendorRepository.save(Vendor.builder().name("테스트 업체").build());
		Product product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("동시성 키 테스트 카드").price(1000L).build());
		stockRepository.save(Stock.builder().productId(product.getId())
				.totalQuantity(TOTAL_STOCK).reservedQuantity(0).soldQuantity(0).build());
		String sharedKey = java.util.UUID.randomUUID().toString();

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		List<OrderResponse> responses = new CopyOnWriteArrayList<>();
		AtomicInteger processingCount = new AtomicInteger();
		AtomicInteger unexpectedFailureCount = new AtomicInteger();

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					CreateOrderRequest request = new CreateOrderRequest(product.getId(), 1);
					responses.add(orderService.createOrder(buyer.getId(), request, sharedKey));
				} catch (CustomException e) {
					if (e.getErrorCode() == OrderErrorCode.ORDER_CREATION_IN_PROGRESS) {
						processingCount.incrementAndGet();
					} else {
						unexpectedFailureCount.incrementAndGet();
					}
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
		assertThat(responses.size() + processingCount.get()).isEqualTo(CONCURRENT_REQUESTS);
		assertThat(responses).isNotEmpty();
		assertThat(responses.stream().map(OrderResponse::getOrderNumber).distinct().count()).isEqualTo(1);

		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(1);
	}

	private String uniqueEmail() {
		return "user-" + java.util.UUID.randomUUID() + "@test.com";
	}

}
