package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import feign.FeignException;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
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
class PaymentApprovalServiceTest extends RedisTestContainer {

	@Autowired
	private PaymentApprovalService paymentApprovalService;

	@Autowired
	private PaymentStateService paymentStateService;

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
				Product.builder().vendorId(vendor.getId()).name("피카츄 카드").price(10_000L).build());
		productId = product.getId();
		stockRepository.save(Stock.builder().productId(productId).totalQuantity(10).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("PG 승인이 성공하면 결제/주문이 완료되고 사가 스텝이 전부 성공한다")
	void approve_success() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-1", "ORDER-TEST", "DONE", 10_000L, LocalDateTime.now()));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-TX-1", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name());

		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
		assertThat(stock.getReservedQuantity()).isZero();

		assertThat(pointBalanceRepository.findByMemberId(buyer.getId())).isPresent();
	}

	@Test
	@DisplayName("PG가 4xx로 거절해도 order/재고는 그대로 남아 같은 주문으로 재시도할 수 있다")
	void approve_pgUserFault() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		FeignException badRequest = mock(FeignException.class);
		when(badRequest.status()).thenReturn(400);
		when(pgClient.approve(any(), any())).thenThrow(badRequest);

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-A", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED.name());
		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(1);
		assertThat(stock.availableQuantity()).isEqualTo(9);
	}

	@Test
	@DisplayName("첫 결제수단이 거절돼도 같은 주문에 새 Idempotency-Key로 재시도하면 승인될 수 있다")
	void approve_retryAfterUserFault_succeeds() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		FeignException badRequest = mock(FeignException.class);
		when(badRequest.status()).thenReturn(400);
		when(pgClient.approve(any(), any()))
				.thenThrow(badRequest)
				.thenReturn(new ApprovalResponse("PG-TX-RETRY", "ORDER-TEST", "DONE", 10_000L, LocalDateTime.now()));

		PaymentResponse firstAttempt = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-B", 0L, 10_000L));
		assertThat(firstAttempt.getStatus()).isEqualTo(PaymentStatus.FAILED.name());

		PaymentResponse secondAttempt = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-C", 0L, 10_000L));

		assertThat(secondAttempt.getStatus()).isEqualTo(PaymentStatus.DONE.name());
		assertThat(secondAttempt.getId()).isNotEqualTo(firstAttempt.getId());

		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
		assertThat(stock.getReservedQuantity()).isZero();
	}

	@Test
	@DisplayName("같은 요청을 같은 주문에 동시에 두 번 보내도 승인은 한 번만 나가고 같은 응답을 재사용한다")
	void approve_sameRequestRetried_reusesCachedResult() {
		OrderResponse order = createOrder(1);
		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-1", "ORDER-TEST", "DONE", 10_000L, LocalDateTime.now()));

		PaymentResponse first = paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), new ApprovePaymentRequest("PG-TX-SAME", 0L, 10_000L));
		PaymentResponse second = paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), new ApprovePaymentRequest("PG-TX-SAME", 0L, 10_000L));

		assertThat(first.getId()).isEqualTo(second.getId());
		verify(pgClient, times(1)).approve(any(), any());
	}

	@Test
	@DisplayName("PG 호출이 재시도까지 전부 실패하면 결제는 TIMEOUT_UNKNOWN으로 남고 주문은 PAYMENT_PENDING을 유지한다")
	void approve_timeoutUnknown() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		when(pgClient.approve(any(), any())).thenThrow(new RuntimeException("connection refused"));
		when(pgClient.inquire(any())).thenThrow(new RuntimeException("connection refused"));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-F", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.TIMEOUT_UNKNOWN.name());
		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
	}

	@Test
	@DisplayName("PG 호출은 실패해도 즉시 재조회에서 승인이 확인되면 그 자리에서 결제를 완료 처리한다")
	void approve_ambiguousFailure_confirmedDoneByImmediateInquiry() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		when(pgClient.approve(any(), any())).thenThrow(new RuntimeException("connection refused"));
		when(pgClient.inquire(any()))
				.thenReturn(new ApprovalResponse("PG-TX-INQUIRY", "ORDER-TEST", "DONE", 10_000L, LocalDateTime.now()));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-INQUIRY-DONE", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name());
		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
		assertThat(stock.getReservedQuantity()).isZero();
	}

	@Test
	@DisplayName("PG 호출은 실패해도 즉시 재조회에서 명확한 실패가 확인되면 그 자리에서 결제를 실패 처리한다")
	void approve_ambiguousFailure_confirmedFailedByImmediateInquiry() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		when(pgClient.approve(any(), any())).thenThrow(new RuntimeException("connection refused"));
		when(pgClient.inquire(any()))
				.thenReturn(new ApprovalResponse("PG-TX-INQUIRY", "ORDER-TEST", "ABORTED", 10_000L, null));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-INQUIRY-FAILED", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED.name());
		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(1);
	}

	@Test
	@DisplayName("승인 요청 금액이 서버가 계산한 금액과 다르면 PG 호출 없이 즉시 거절된다")
	void approve_amountMismatch_rejectedBeforePgCall() {
		OrderResponse order0 = createOrder(1);

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(),
				new ApprovePaymentRequest("PG-KEY-MISMATCH", 0L, 9_999L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PaymentErrorCode.AUTHORIZED_AMOUNT_MISMATCH);

		verify(pgClient, never()).approve(any(), any());

		Order order = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
	}

	@Test
	@DisplayName("같은 주문에 이미 처리 중(IN_PROGRESS)인 결제 시도가 있으면 새 승인 요청은 PG 호출 없이 즉시 거절된다")
	void approve_anotherPaymentAlreadyInProgress_rejected() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();

		paymentStateService.initiate(orderId, UUID.randomUUID().toString(), 10_000L, 0L, "PG-KEY-INFLIGHT", "mock-pg");

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(),
				new ApprovePaymentRequest("PG-KEY-NEW", 0L, 10_000L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);

		verify(pgClient, never()).approve(any(), any());
	}

	@Test
	@DisplayName("결제 승인 시점에 예약이 만료돼 있으면 PG 호출 없이 즉시 거절되고, 재고는 그 자리에서 복구된다(lazy expiration)")
	void approve_reservationExpired_releasesStockImmediately() {
		OrderResponse order0 = createOrder(1);
		Order order = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow();
		ReflectionTestUtils.setField(order, "expiresAt", LocalDateTime.now().minusMinutes(1));
		orderRepository.save(order);

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-EXPIRED", 0L, 10_000L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(OrderErrorCode.ORDER_EXPIRED);

		verify(pgClient, never()).approve(any(), any());

		Order persisted = orderRepository.findById(order.getId()).orElseThrow();
		assertThat(persisted.getStatus()).isEqualTo(OrderStatus.EXPIRED);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getReservedQuantity()).isZero();
		assertThat(stock.availableQuantity()).isEqualTo(10);
	}

	@Test
	@DisplayName("결제 상태 조회는 가장 최근 결제 시도의 상태를 반환한다")
	void getStatus_returnsLatestPaymentAttempt() {
		OrderResponse order0 = createOrder(1);
		FeignException badRequest = mock(FeignException.class);
		when(badRequest.status()).thenReturn(400);
		when(pgClient.approve(any(), any()))
				.thenThrow(badRequest)
				.thenReturn(new ApprovalResponse("PG-TX-STATUS", "ORDER-TEST", "DONE", 10_000L, LocalDateTime.now()));

		paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-STATUS-1", 0L, 10_000L));
		paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), new ApprovePaymentRequest("PG-KEY-STATUS-2", 0L, 10_000L));

		PaymentResponse status = paymentApprovalService.getStatus(buyer.getId(), order0.getOrderNumber());

		assertThat(status.getStatus()).isEqualTo(PaymentStatus.DONE.name());
	}

	@Test
	@DisplayName("아직 결제를 시도하지 않은 주문의 상태를 조회하면 PAYMENT_NOT_FOUND를 반환한다")
	void getStatus_noPaymentYet_throwsNotFound() {
		OrderResponse order0 = createOrder(1);

		assertThatThrownBy(() -> paymentApprovalService.getStatus(buyer.getId(), order0.getOrderNumber()))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("다른 회원이 결제 상태를 조회하면 ORDER_NOT_FOUND를 반환한다")
	void getStatus_notOwner_throwsOrderNotFound() {
		OrderResponse order0 = createOrder(1);
		Member stranger = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("타인").role(MemberRole.USER).build());

		assertThatThrownBy(() -> paymentApprovalService.getStatus(stranger.getId(), order0.getOrderNumber()))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
	}

	private OrderResponse createOrder(int quantity) {
		CreateOrderRequest request = new CreateOrderRequest(productId, quantity);
		return orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
