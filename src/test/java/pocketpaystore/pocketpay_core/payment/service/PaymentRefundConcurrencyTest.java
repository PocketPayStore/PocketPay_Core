package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentCancel;
import pocketpaystore.pocketpay_core.payment.domain.PaymentMethod;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.CreateRefundRequest;
import pocketpaystore.pocketpay_core.payment.repository.PaymentCancelRepository;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.CancelResponse;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRefundConcurrencyTest {

	private static final int CONCURRENT_REQUESTS = 15;
	private static final int QUANTITY_PER_REQUEST = 1;
	private static final long UNIT_PRICE = 1_000L;
	private static final long REFUND_AMOUNT_PER_REQUEST = UNIT_PRICE * QUANTITY_PER_REQUEST;
	private static final long PAYMENT_AMOUNT = 10_000L;

	@Autowired
	private PaymentRefundService paymentRefundService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentCancelRepository paymentCancelRepository;

	@MockitoBean
	private PgClient pgClient;

	@BeforeEach
	void setUp() {
		when(pgClient.cancel(any())).thenReturn(new CancelResponse("PG-TX-REFUND", "0000", LocalDateTime.now()));
	}

	@Test
	@DisplayName("동일 결제 건에 부분환불 요청이 동시에 들어와도 초과 환불 없이 정확히 반영된다")
	void concurrentPartialRefunds_noExcessiveRefund() throws InterruptedException {
		Member buyer = memberRepository.save(
				Member.builder().email("buyer-" + UUID.randomUUID() + "@test.com")
						.password("test1234").name("구매자").role(MemberRole.USER).build());
		Member seller = memberRepository.save(
				Member.builder().email("seller-" + UUID.randomUUID() + "@test.com")
						.password("test1234").name("판매자").role(MemberRole.USER).build());
		Product product = productRepository.save(
				Product.builder().sellerId(seller.getId()).name("환불 테스트 카드").price(UNIT_PRICE).build());

		Order order = orderRepository.save(
				Order.create("ORD-" + UUID.randomUUID(), buyer.getId(), PAYMENT_AMOUNT, UUID.randomUUID().toString()));
		String orderNumber = order.getOrderNumber();
		orderItemRepository.save(OrderItem.create(order.getId(), product.getId(), 10, UNIT_PRICE));

		Payment payment = Payment.create(order.getId(), PaymentMethod.CARD, "mock-pg",
				UUID.randomUUID().toString(), PAYMENT_AMOUNT, 0L, "PG-TX-ORIGINAL");
		payment.toInProgress();
		payment.toDone();
		payment = paymentRepository.save(payment);
		Long paymentId = payment.getId();

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger rejectedCount = new AtomicInteger();
		AtomicInteger unexpectedFailureCount = new AtomicInteger();

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			String idempotencyKey = UUID.randomUUID().toString();
			CreateRefundRequest request = new CreateRefundRequest(QUANTITY_PER_REQUEST, "단순 변심");
			executor.submit(() -> {
				try {
					startLatch.await();
					paymentRefundService.refund(buyer.getId(), orderNumber, request, idempotencyKey);
					successCount.incrementAndGet();
				} catch (CustomException e) {
					if (e.getErrorCode() == PaymentErrorCode.EXCESSIVE_REFUND_AMOUNT) {
						rejectedCount.incrementAndGet();
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
		assertThat(successCount.get() + rejectedCount.get()).isEqualTo(CONCURRENT_REQUESTS);

		long expectedRefunded = successCount.get() * REFUND_AMOUNT_PER_REQUEST;
		assertThat(expectedRefunded).isLessThanOrEqualTo(PAYMENT_AMOUNT);

		Payment finalPayment = paymentRepository.findById(paymentId).orElseThrow();
		assertThat(finalPayment.getRefundableAmount()).isEqualTo(PAYMENT_AMOUNT - expectedRefunded);
		assertThat(finalPayment.getStatus()).isIn(PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED);

		List<PaymentCancel> cancels = paymentCancelRepository.findAll();
		long totalCanceled = cancels.stream()
				.filter(c -> c.getPaymentId().equals(paymentId))
				.mapToLong(PaymentCancel::getCancelAmount)
				.sum();
		assertThat(totalCanceled).isEqualTo(expectedRefunded);
	}

}
