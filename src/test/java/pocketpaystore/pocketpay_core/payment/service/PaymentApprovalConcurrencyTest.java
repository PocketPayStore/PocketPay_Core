package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderStatus;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.order.service.OrderService;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.ApprovePaymentRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.PaymentResponse;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.response.ApprovalResponse;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentApprovalConcurrencyTest extends RedisTestContainer {

	private static final int CONCURRENT_REQUESTS = 10;
	private static final long UNIT_PRICE = 10_000L;

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
	private OrderRepository orderRepository;

	@Autowired
	private PointBalanceRepository pointBalanceRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@MockitoBean
	private PgClient pgClient;

	private Member buyer;
	private Long productId;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		pointBalanceRepository.save(PointBalance.create(buyer.getId()));
		Vendor vendor = vendorRepository.save(Vendor.builder().name("테스트 업체").build());
		Product product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("동시 승인 테스트 카드").price(UNIT_PRICE).build());
		productId = product.getId();
		stockRepository.save(Stock.builder().productId(productId).totalQuantity(10).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("같은 결제 요청이 동시에 여러 번 들어와도 PG 승인은 한 번만 나가고 결제는 정확히 하나만 DONE이 된다")
	void concurrentApproveWithSamePaymentKey_onlyOnePaymentBecomesDone() throws InterruptedException {
		OrderResponse order = createOrder(1);
		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-CONCURRENT", "ORDER-TEST", "DONE", 10_000L, LocalDateTime.now()));
		ApprovePaymentRequest request = new ApprovePaymentRequest("PG-KEY-CONCURRENT", 0L, UNIT_PRICE);

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		List<PaymentResponse> responses = new CopyOnWriteArrayList<>();
		AtomicInteger unexpectedFailureCount = new AtomicInteger();

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					responses.add(paymentApprovalService.approve(buyer.getId(), order.getOrderNumber(), request));
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
		assertThat(responses).hasSize(CONCURRENT_REQUESTS);
		assertThat(responses.stream().map(PaymentResponse::getId).distinct().count()).isEqualTo(1);
		assertThat(responses).allSatisfy(response -> assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name()));

		verify(pgClient, times(1)).approve(any(), any());

		Order finalOrder = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.PAID);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
		assertThat(stock.getReservedQuantity()).isZero();
	}

	private OrderResponse createOrder(int quantity) {
		CreateOrderRequest request = new CreateOrderRequest(productId, quantity);
		return orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
