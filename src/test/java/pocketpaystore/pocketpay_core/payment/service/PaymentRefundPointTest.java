package pocketpaystore.pocketpay_core.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
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
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.CreateRefundRequest;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.response.CancelResponse;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.domain.PointEarnLog;
import pocketpaystore.pocketpay_core.point.domain.PointEarnStatus;
import pocketpaystore.pocketpay_core.point.domain.PointLedger;
import pocketpaystore.pocketpay_core.point.domain.PointLedgerType;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.point.repository.PointEarnLogRepository;
import pocketpaystore.pocketpay_core.point.repository.PointLedgerRepository;
import pocketpaystore.pocketpay_core.point.service.PointEarnApplyService;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRefundPointTest extends RedisTestContainer {

	private static final long UNIT_PRICE = 1_000L;
	private static final int TOTAL_QUANTITY = 10;
	private static final long ORDER_TOTAL = UNIT_PRICE * TOTAL_QUANTITY;
	private static final long USED_POINT_AMOUNT = 4_000L;
	private static final long PG_AMOUNT = ORDER_TOTAL - USED_POINT_AMOUNT;
	private static final long ORIGINAL_EARN_AMOUNT = 60L;
	private static final long BASELINE_BALANCE = 500L;

	@Autowired
	private PaymentRefundService paymentRefundService;

	@Autowired
	private PointEarnApplyService pointEarnApplyService;

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
	private PointBalanceRepository pointBalanceRepository;

	@Autowired
	private PointLedgerRepository pointLedgerRepository;

	@Autowired
	private PointEarnLogRepository pointEarnLogRepository;

	@MockitoBean
	private PgClient pgClient;

	@Test
	void fullRefund_restoresPointsAndReversesResolvedEarn() {
		when(pgClient.cancel(any(), any(), any())).thenReturn(new CancelResponse("PG-TX-REFUND", "DONE"));

		Member buyer = saveBuyer();
		Order order = saveOrderWithItem(buyer.getId());
		Payment payment = savePayment(order.getId());
		pointBalanceRepository.save(givenBalance(buyer.getId(), BASELINE_BALANCE));
		Long logId = savePointEarnLog(buyer.getId(), order.getId(), payment.getId(), PointEarnStatus.RESOLVED);

		paymentRefundService.refund(buyer.getId(), order.getOrderNumber(),
				new CreateRefundRequest(TOTAL_QUANTITY, "전체 환불"));

		Payment refundedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(refundedPayment.getRefundableAmount()).isZero();
		assertThat(refundedPayment.getRefundablePointAmount()).isZero();

		PointBalance balance = pointBalanceRepository.findByMemberId(buyer.getId()).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(BASELINE_BALANCE + USED_POINT_AMOUNT - ORIGINAL_EARN_AMOUNT);

		List<PointLedger> ledgers = ledgersForOrder(order.getId());
		assertThat(ledgers).anyMatch(l -> l.getType() == PointLedgerType.CANCEL_RESTORE
				&& l.getAmount().equals(USED_POINT_AMOUNT));
		assertThat(ledgers).anyMatch(l -> l.getType() == PointLedgerType.EARN_REVERSAL
				&& l.getAmount().equals(-ORIGINAL_EARN_AMOUNT));

		PointEarnLog earnLog = pointEarnLogRepository.findById(logId).orElseThrow();
		assertThat(earnLog.getReversedAmount()).isEqualTo(ORIGINAL_EARN_AMOUNT);
		assertThat(earnLog.remainingAmount()).isZero();
		assertThat(earnLog.getStatus()).isEqualTo(PointEarnStatus.RESOLVED);
	}

	@Test
	void partialRefund_restoresProportionalPointsAndReducesPendingEarn() {
		when(pgClient.cancel(any(), any(), any())).thenReturn(new CancelResponse("PG-TX-REFUND", "DONE"));

		Member buyer = saveBuyer();
		Order order = saveOrderWithItem(buyer.getId());
		Payment payment = savePayment(order.getId());
		pointBalanceRepository.save(givenBalance(buyer.getId(), BASELINE_BALANCE));
		Long logId = savePointEarnLog(buyer.getId(), order.getId(), payment.getId(), PointEarnStatus.PENDING);

		int halfQuantity = TOTAL_QUANTITY / 2;
		paymentRefundService.refund(buyer.getId(), order.getOrderNumber(),
				new CreateRefundRequest(halfQuantity, "부분 환불"));

		Payment refundedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);

		long expectedPointRestore = USED_POINT_AMOUNT / 2;
		PointBalance balance = pointBalanceRepository.findByMemberId(buyer.getId()).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(BASELINE_BALANCE + expectedPointRestore);

		List<PointLedger> ledgers = ledgersForOrder(order.getId());
		assertThat(ledgers).anyMatch(l -> l.getType() == PointLedgerType.CANCEL_RESTORE
				&& l.getAmount().equals(expectedPointRestore));
		assertThat(ledgers).noneMatch(l -> l.getType() == PointLedgerType.EARN_REVERSAL);

		long expectedEarnReversal = ORIGINAL_EARN_AMOUNT / 2;
		PointEarnLog earnLog = pointEarnLogRepository.findById(logId).orElseThrow();
		assertThat(earnLog.getStatus()).isEqualTo(PointEarnStatus.PENDING);
		assertThat(earnLog.getReversedAmount()).isEqualTo(expectedEarnReversal);
		assertThat(earnLog.remainingAmount()).isEqualTo(ORIGINAL_EARN_AMOUNT - expectedEarnReversal);

		boolean applied = pointEarnApplyService.apply(logId);

		assertThat(applied).isTrue();
		PointBalance balanceAfterApply = pointBalanceRepository.findByMemberId(buyer.getId()).orElseThrow();
		assertThat(balanceAfterApply.getBalance())
				.isEqualTo(BASELINE_BALANCE + expectedPointRestore + (ORIGINAL_EARN_AMOUNT - expectedEarnReversal));
		assertThat(pointEarnLogRepository.findById(logId).orElseThrow().getStatus())
				.isEqualTo(PointEarnStatus.RESOLVED);
	}

	private List<PointLedger> ledgersForOrder(Long orderId) {
		return pointLedgerRepository.findAll().stream()
				.filter(l -> orderId.equals(l.getOrderId()))
				.toList();
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
				UUID.randomUUID().toString(), PG_AMOUNT, USED_POINT_AMOUNT, "PG-TX-ORIGINAL");
		payment.toInProgress();
		payment.toDone();
		return paymentRepository.save(payment);
	}

	private PointBalance givenBalance(Long memberId, long balance) {
		PointBalance pointBalance = PointBalance.create(memberId);
		pointBalance.adjust(balance);
		return pointBalance;
	}

	private Long savePointEarnLog(Long memberId, Long orderId, Long paymentId, PointEarnStatus status) {
		PointEarnLog earnLog = PointEarnLog.create(memberId, orderId, paymentId, ORIGINAL_EARN_AMOUNT);
		if (status == PointEarnStatus.RESOLVED) {
			earnLog.markProcessing();
			earnLog.markResolved();
		}
		return pointEarnLogRepository.save(earnLog).getId();
	}

}
