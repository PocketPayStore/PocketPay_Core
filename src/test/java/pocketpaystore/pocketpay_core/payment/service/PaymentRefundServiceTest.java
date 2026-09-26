package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentMethod;
import pocketpaystore.pocketpay_core.payment.domain.Refund;
import pocketpaystore.pocketpay_core.payment.domain.RefundStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.CreateRefundRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.RefundResponse;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.payment.repository.RefundRepository;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.response.CancelResponse;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRefundServiceTest extends RedisTestContainer {

	private static final long UNIT_PRICE = 1_000L;
	private static final int TOTAL_QUANTITY = 5;
	private static final long ORDER_TOTAL = UNIT_PRICE * TOTAL_QUANTITY;

	@Autowired
	private PaymentRefundService paymentRefundService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RefundRepository refundRepository;

	@MockitoBean
	private PgClient pgClient;

	@Test
	void refund_pgCancelSucceeds_marksPgCancelConfirmed() {
		when(pgClient.cancel(any(), any(), any())).thenReturn(new CancelResponse("PG-TX-REFUND", "DONE"));

		Member buyer = saveBuyer();
		Order order = saveOrderWithItem(buyer.getId());
		savePayment(order.getId());

		RefundResponse response = paymentRefundService.refund(buyer.getId(), order.getOrderNumber(),
				new CreateRefundRequest(TOTAL_QUANTITY, "전체 환불"));

		Refund refund = refundRepository.findById(response.getRefundId()).orElseThrow();
		assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
		assertThat(refund.isPgCancelConfirmed()).isTrue();
	}

	@Test
	void refund_pgCancelFails_completesLocallyButLeavesPgCancelUnconfirmed() {
		when(pgClient.cancel(any(), any(), any())).thenThrow(new RuntimeException("connection refused"));

		Member buyer = saveBuyer();
		Order order = saveOrderWithItem(buyer.getId());
		savePayment(order.getId());

		RefundResponse response = paymentRefundService.refund(buyer.getId(), order.getOrderNumber(),
				new CreateRefundRequest(TOTAL_QUANTITY, "전체 환불"));

		Refund refund = refundRepository.findById(response.getRefundId()).orElseThrow();
		assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
		assertThat(refund.isPgCancelConfirmed()).isFalse();
	}

	private Member saveBuyer() {
		return memberRepository.save(Member.builder()
				.email("refund-" + UUID.randomUUID() + "@test.com")
				.password("test1234").name("환불테스트").role(MemberRole.USER).build());
	}

	private Order saveOrderWithItem(Long memberId) {
		Vendor vendor = vendorRepository.save(Vendor.builder().name("환불 테스트 업체").build());
		Product product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("환불 테스트 카드").price(UNIT_PRICE).build());
		Order order = orderRepository.save(Order.create(
				"ORD-" + UUID.randomUUID(), memberId, ORDER_TOTAL, UUID.randomUUID().toString(),
				LocalDateTime.now().plusMinutes(10)));
		orderItemRepository.save(OrderItem.create(order.getId(), product.getId(), TOTAL_QUANTITY, UNIT_PRICE));
		return order;
	}

	private Payment savePayment(Long orderId) {
		Payment payment = Payment.create(orderId, PaymentMethod.CARD, "mock-pg",
				UUID.randomUUID().toString(), ORDER_TOTAL, 0L, "PG-TX-ORIGINAL");
		payment.toInProgress();
		payment.toDone();
		return paymentRepository.save(payment);
	}

}
