package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import pocketpaystore.pocketpay_core.pg.dto.ApprovalResponse;
import pocketpaystore.pocketpay_core.pg.dto.CancelResponse;
import pocketpaystore.pocketpay_core.point.service.PointService;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.saga.domain.SagaLog;
import pocketpaystore.pocketpay_core.saga.domain.SagaStatus;
import pocketpaystore.pocketpay_core.saga.domain.SagaStep;
import pocketpaystore.pocketpay_core.saga.repository.SagaLogRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentSagaCompensationTest extends RedisTestContainer {

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
	private SagaLogRepository sagaLogRepository;

	@MockitoBean
	private PgClient pgClient;

	@MockitoBean
	private PointService pointService;

	private Member buyer;
	private Long productId;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Member seller = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("판매자").role(MemberRole.USER).build());
		Product product = productRepository.save(
				Product.builder().sellerId(seller.getId()).name("나인테일 카드").price(20_000L).build());
		productId = product.getId();
		stockRepository.save(Stock.builder().productId(productId).totalQuantity(5).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("포인트 적립이 강제로 실패하면 보상 트랜잭션이 결제/주문을 취소하고 재고를 원복한다")
	void pointEarnFailure_triggersCompensation() {
		CreateOrderRequest request = new CreateOrderRequest(productId, 2);
		OrderResponse orderResponse = orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
		Long orderId = orderRepository.findByOrderNumber(orderResponse.getOrderNumber()).orElseThrow().getId();

		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-2", "0000", "OK", LocalDateTime.now()));
		when(pgClient.cancel(any())).thenReturn(new CancelResponse("PG-TX-2", "0000", LocalDateTime.now()));
		doThrow(new RuntimeException("포인트 시스템 장애")).when(pointService).earn(any(), any(), any());

		PaymentResponse response = paymentApprovalService.approve(
				buyer.getId(), orderResponse.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-TX-2", 0L, 40_000L));

		assertThat(response).isNotNull();

		Payment payment = paymentRepository.findById(response.getId()).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);

		Order order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

		Stock stock = stockRepository.findByProductId(productId).orElseThrow();
		assertThat(stock.getReservedQuantity()).isZero();
		assertThat(stock.getSoldQuantity()).isZero();
		assertThat(stock.availableQuantity()).isEqualTo(5);

		verify(pgClient, times(1)).cancel(any());

		List<SagaLog> logs = sagaLogRepository.findByOrderId(orderId);
		assertThat(logs).filteredOn(l -> l.getStep() == SagaStep.POINT_EARN)
				.extracting(SagaLog::getStatus)
				.containsExactly(SagaStatus.FAILED);
		assertThat(logs).filteredOn(l -> l.getStep() == SagaStep.PAYMENT)
				.extracting(SagaLog::getStatus)
				.containsExactly(SagaStatus.COMPENSATED);
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
