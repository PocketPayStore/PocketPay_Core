package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.service.OrderService;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.ApprovePaymentRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.PaymentResponse;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.ApprovalResponse;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentSagaConcurrencyTest {

	private static final int CONCURRENT_REQUESTS = 100;
	private static final int THREAD_POOL_SIZE = CONCURRENT_REQUESTS;

	@Autowired
	private PaymentApprovalService paymentApprovalService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PointBalanceRepository pointBalanceRepository;

	@MockitoBean
	private PgClient pgClient;

	@BeforeEach
	void setUp() {
		when(pgClient.approve(any(), any()))
				.thenReturn(new ApprovalResponse("PG-TX-CONCURRENT", "0000", "OK", LocalDateTime.now()));
	}

	@Test
	@DisplayName("동일 Idempotency-Key로 결제 승인 요청 100건이 동시에 들어오면 실제 처리는 1번만 되고 전부 같은 성공 응답을 받는다")
	void concurrentApprove_sameIdempotencyKey_sharesOneResult() throws InterruptedException {
		Member buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		pointBalanceRepository.save(PointBalance.create(buyer.getId()));
		Member seller = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("판매자").role(MemberRole.USER).build());
		Product product = productRepository.save(
				Product.builder().sellerId(seller.getId()).name("골드스타 카드").price(1000L).build());
		stockRepository.save(Stock.builder().productId(product.getId()).totalQuantity(200).reservedQuantity(0).soldQuantity(0).build());

		String orderNumber = orderService.createOrder(buyer.getId(),
				new CreateOrderRequest(product.getId(), 1),
				UUID.randomUUID().toString()).getOrderNumber();

		String sharedIdempotencyKey = UUID.randomUUID().toString();

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		List<PaymentResponse> responses = new CopyOnWriteArrayList<>();
		AtomicInteger failureCount = new AtomicInteger();

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			executor.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await();
					responses.add(paymentApprovalService.approve(buyer.getId(), orderNumber, sharedIdempotencyKey, new ApprovePaymentRequest("PG-TX-CONCURRENT", 0L, 1_000L)));
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					doneLatch.countDown();
				}
			});
		}
		boolean allReady = readyLatch.await(10, TimeUnit.SECONDS);
		startLatch.countDown();
		boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(allReady).isTrue();
		assertThat(finished).isTrue();
		assertThat(failureCount.get()).isZero();
		assertThat(responses).hasSize(CONCURRENT_REQUESTS);

		Long firstPaymentId = responses.get(0).getId();
		assertThat(responses).allSatisfy(response -> {
			assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name());
			assertThat(response.getId()).isEqualTo(firstPaymentId);
		});

		assertThat(paymentRepository.findByIdempotencyKey(sharedIdempotencyKey)).isPresent();
		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
