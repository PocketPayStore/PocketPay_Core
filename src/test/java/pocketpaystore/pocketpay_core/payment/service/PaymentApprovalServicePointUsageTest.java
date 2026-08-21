package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderStatus;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.order.service.OrderService;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.ApprovePaymentRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.PaymentResponse;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.response.ApprovalResponse;
import pocketpaystore.pocketpay_core.pg.dto.response.CancelResponse;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.product.service.StockService;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentApprovalServicePointUsageTest extends RedisTestContainer {

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
	private PaymentRepository paymentRepository;

	@Autowired
	private PointBalanceRepository pointBalanceRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@MockitoBean
	private PgClient pgClient;

	@MockitoBean
	private StockService stockService;

	private Member buyer;
	private Long productId;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Vendor vendor = vendorRepository.save(Vendor.builder().name("테스트 업체").build());
		Product product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("이상해씨 카드").price(10_000L).build());
		productId = product.getId();
		stockRepository.save(Stock.builder().productId(productId).totalQuantity(10).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("포인트를 일부 사용하면 PG에는 차감된 금액만 승인 요청되고, 포인트 잔액도 그만큼 줄어든다")
	void usePoints_partialPayment_succeeds() {
		givenPointBalance(3_000L);
		OrderResponse order = createOrder(1);

		when(pgClient.approve(any(), argThat(req -> req.getAmount() == 7_000L)))
				.thenReturn(new ApprovalResponse("PG-TX-POINT", "0000", "OK", LocalDateTime.now()));

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-PARTIAL", 3_000L, 7_000L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name());
		assertThat(response.getAmount()).isEqualTo(7_000L);
		assertThat(response.getUsedPointAmount()).isEqualTo(3_000L);

		Payment payment = paymentRepository.findById(response.getId()).orElseThrow();
		assertThat(payment.getAmount()).isEqualTo(7_000L);
		assertThat(payment.getUsedPointAmount()).isEqualTo(3_000L);

		PointBalance balance = pointBalanceRepository.findByMemberId(buyer.getId()).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(70L);

		Order orderEntity = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(orderEntity.getStatus()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	@DisplayName("주문 금액 전체를 포인트로 결제하면 PG 승인 호출 자체를 건너뛰고 바로 완료된다")
	void usePoints_fullyCoveredByPoints_skipsPgCall() {
		givenPointBalance(10_000L);
		OrderResponse order = createOrder(1);

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), UUID.randomUUID().toString(),
				new ApprovePaymentRequest("PG-KEY-FULL-POINTS", 10_000L, 0L));

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE.name());
		assertThat(response.getAmount()).isZero();
		assertThat(response.getUsedPointAmount()).isEqualTo(10_000L);
		assertThat(response.getPgTransactionId()).isEqualTo("PG-KEY-FULL-POINTS");

		verify(pgClient, never()).approve(any(), any());

		PointBalance balance = pointBalanceRepository.findByMemberId(buyer.getId()).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(0L);
	}

	@Test
	@DisplayName("보유 포인트보다 많은 포인트를 사용하려 하면 PG 호출 전에 즉시 거절된다")
	void usePoints_insufficientBalance_rejectedBeforePgCall() {
		givenPointBalance(1_000L);
		OrderResponse order = createOrder(1);

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-INSUFFICIENT", 3_000L, 7_000L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PointErrorCode.INSUFFICIENT_POINT_BALANCE);

		verify(pgClient, never()).approve(any(), any());
	}

	@Test
	@DisplayName("주문 금액보다 많은 포인트를 사용하려 하면 즉시 거절된다")
	void usePoints_exceedsOrderTotal_rejected() {
		givenPointBalance(20_000L);
		OrderResponse order = createOrder(1);

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-EXCESS", 15_000L, 0L)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PaymentErrorCode.INVALID_POINT_USE_AMOUNT);

		verify(pgClient, never()).approve(any(), any());
	}

	@Test
	@DisplayName("포인트 사용 후 재고 확정이 실패하면 보상 트랜잭션이 사용한 포인트를 되돌린다")
	void usePoints_compensatedWhenLaterStepFails() {
		givenPointBalance(3_000L);
		OrderResponse order = createOrder(1);

		when(pgClient.approve(any(), any()))
				.thenReturn(new ApprovalResponse("PG-TX-COMPENSATE", "0000", "OK", LocalDateTime.now()));
		when(pgClient.cancel(any())).thenReturn(new CancelResponse("PG-TX-COMPENSATE", "0000", LocalDateTime.now()));
		doThrow(new RuntimeException("재고 확정 실패")).when(stockService).confirmForOrder(any());

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-KEY-COMPENSATE", 3_000L, 7_000L));

		assertThat(response).isNotNull();

		Payment payment = paymentRepository.findById(response.getId()).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);

		PointBalance balance = pointBalanceRepository.findByMemberId(buyer.getId()).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(3_000L);
	}

	private void givenPointBalance(Long amount) {
		PointBalance balance = pointBalanceRepository.save(PointBalance.create(buyer.getId()));
		balance.adjust(amount);
		pointBalanceRepository.save(balance);
	}

	private OrderResponse createOrder(int quantity) {
		CreateOrderRequest request = new CreateOrderRequest(productId, quantity);
		return orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
