package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

import pocketpaystore.pocketpay_core.common.alert.CriticalAlertService;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.order.service.OrderService;
import pocketpaystore.pocketpay_core.payment.dto.request.ApprovePaymentRequest;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.ApprovalResponse;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentApprovalServiceCriticalFailureTest {

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

	@MockitoBean
	private PgClient pgClient;

	@MockitoBean
	private PaymentStateService paymentStateService;

	@MockitoBean
	private CriticalAlertService criticalAlertService;

	private Member buyer;
	private Long productId;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Member seller = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("판매자").role(MemberRole.USER).build());
		Product product = productRepository.save(
				Product.builder().sellerId(seller.getId()).name("뮤츠 카드").price(15_000L).build());
		productId = product.getId();
		stockRepository.save(Stock.builder().productId(productId).totalQuantity(5).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("PG 승인은 성공했는데 결제 상태 저장이 실패하면 CriticalAlertService로 알리고 예외를 그대로 전파한다")
	void approve_pgSucceedsButPersistFails_alertsAndPropagates() {
		CreateOrderRequest request = new CreateOrderRequest(productId, 1);
		OrderResponse order = orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
		Long orderId = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow().getId();

		when(paymentStateService.initiate(anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyString()))
				.thenReturn(1L);
		when(pgClient.approve(any(), any())).thenReturn(new ApprovalResponse("PG-TX-CRASH", "0000", "OK", LocalDateTime.now()));
		RuntimeException dbFailure = new RuntimeException("DB connection lost");
		when(paymentStateService.markDone(anyLong(), anyLong())).thenThrow(dbFailure);

		assertThatThrownBy(() -> paymentApprovalService.approve(
				buyer.getId(), order.getOrderNumber(), UUID.randomUUID().toString(), new ApprovePaymentRequest("PG-TX-CRASH", 0L, 15_000L)))
				.isSameAs(dbFailure);

		verify(criticalAlertService).alertPgApprovedButPersistFailed(
				orderId, 1L, "PG-TX-CRASH", order.getTotalAmount(), dbFailure);
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
