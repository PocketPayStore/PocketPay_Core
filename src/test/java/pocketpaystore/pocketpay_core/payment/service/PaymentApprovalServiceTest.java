package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import feign.FeignException;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
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
import pocketpaystore.pocketpay_core.saga.domain.SagaLog;
import pocketpaystore.pocketpay_core.saga.domain.SagaStatus;
import pocketpaystore.pocketpay_core.saga.domain.SagaStep;
import pocketpaystore.pocketpay_core.saga.repository.SagaLogRepository;
import pocketpaystore.pocketpay_core.settlement.repository.SettlementRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

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
	private SagaLogRepository sagaLogRepository;

	@Autowired
	private SettlementRepository settlementRepository;

	@MockitoBean
	private PgClient pgClient;

	private Member buyer;
	private Long productId;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		pointBalanceRepository.save(PointBalance.create(buyer.getId()));
		Member seller = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("판매자").role(MemberRole.USER).build());
		Product product = productRepository.save(
				Product.builder().sellerId(seller.getId()).name("피카츄 카드").price(10_000L).build());
		productId = product.getId();
		stockRepository.save(Stock.builder().productId(productId).totalQuantity(10).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("PG 승인이 성공하면 결제/주문이 완료되고 사가 스텝이 전부 성공한다")
	void approve_success() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-1", "0000", "OK", LocalDateTime.now()));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-TX-1", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name());

		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
		assertThat(stock.getReservedQuantity()).isZero();

		assertThat(pointBalanceRepository.findByMemberId(buyer.getId())).isPresent();
		assertThat(settlementRepository.findAll()).filteredOn(s -> s.getPaymentId().equals(response.getId())).hasSize(1);

		List<SagaLog> logs = sagaLogRepository.findByOrderId(orderId);
		assertThat(logs).filteredOn(l -> l.getStep() == SagaStep.POINT_EARN).extracting(SagaLog::getStatus)
				.containsExactly(SagaStatus.SUCCESS);
		assertThat(logs).filteredOn(l -> l.getStep() == SagaStep.STOCK_CONFIRM).extracting(SagaLog::getStatus)
				.containsExactly(SagaStatus.SUCCESS);
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
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-A", 0L, 10_000L));

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
				.thenReturn(new ApprovalResponse("PG-TX-RETRY", "0000", "OK", LocalDateTime.now()));

		PaymentResponse firstAttempt = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-B", 0L, 10_000L));
		assertThat(firstAttempt.getStatus()).isEqualTo(PaymentStatus.FAILED.name());

		PaymentResponse secondAttempt = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-C", 0L, 10_000L));

		assertThat(secondAttempt.getStatus()).isEqualTo(PaymentStatus.DONE.name());
		assertThat(secondAttempt.getId()).isNotEqualTo(firstAttempt.getId());

		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
		assertThat(stock.getReservedQuantity()).isZero();
	}

	@Test
	@DisplayName("이미 다른 주문의 결제 성공 응답을 캐싱한 Idempotency-Key를 다른 주문에 재사용하면 캐시를 반환하지 않고 명시적으로 거절한다")
	void approve_idempotencyKeyReusedForDifferentOrder_rejected() {
		OrderResponse order1 = createOrder(1);
		String reusedKey = UUID.randomUUID().toString();
		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-1", "0000", "OK", LocalDateTime.now()));
		PaymentResponse firstOrderPayment = paymentApprovalService.approve(buyer.getId(), order1.getOrderNumber(), reusedKey, new ApprovePaymentRequest("PG-KEY-D", 0L, 10_000L));
		assertThat(firstOrderPayment.getStatus()).isEqualTo(PaymentStatus.DONE.name());

		OrderResponse order2 = createOrder(1);

		assertThatThrownBy(() -> paymentApprovalService.approve(buyer.getId(), order2.getOrderNumber(), reusedKey, new ApprovePaymentRequest("PG-KEY-E", 0L, 10_000L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);

		Order order2Entity = orderRepository.findByOrderNumber(order2.getOrderNumber()).orElseThrow();
		assertThat(order2Entity.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
	}

	@Test
	@DisplayName("PG 호출이 재시도까지 전부 실패하면 결제는 TIMEOUT_UNKNOWN으로 남고 주문은 PAYMENT_PENDING을 유지한다")
	void approve_timeoutUnknown() {
		OrderResponse order0 = createOrder(1);
		Long orderId = orderRepository.findByOrderNumber(order0.getOrderNumber()).orElseThrow().getId();
		when(pgClient.approve(any(), any())).thenThrow(new RuntimeException("connection refused"));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-F", 0L, 10_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.TIMEOUT_UNKNOWN.name());
		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
	}

	@Test
	@DisplayName("승인 요청 금액이 서버가 계산한 금액과 다르면 PG 호출 없이 즉시 거절된다")
	void approve_amountMismatch_rejectedBeforePgCall() {
		OrderResponse order0 = createOrder(1);

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(),
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
				buyer.getId(), order0.getOrderNumber(), UUID.randomUUID().toString(),
				new ApprovePaymentRequest("PG-KEY-NEW", 0L, 10_000L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);

		verify(pgClient, never()).approve(any(), any());
	}

	private OrderResponse createOrder(int quantity) {
		CreateOrderRequest request = new CreateOrderRequest(productId, quantity);
		return orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
