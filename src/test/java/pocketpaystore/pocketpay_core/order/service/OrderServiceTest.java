package pocketpaystore.pocketpay_core.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.domain.OrderStatus;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderServiceTest extends RedisTestContainer {

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
	private VendorRepository vendorRepository;

	private Member buyer;
	private Product product;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Vendor vendor = vendorRepository.save(Vendor.builder().name("테스트 업체").build());

		product = productRepository.save(
				Product.builder().vendorId(vendor.getId()).name("이상해씨 카드").price(1000L).build());
		stockRepository.save(Stock.builder().productId(product.getId()).totalQuantity(10).reservedQuantity(0).soldQuantity(0).build());
	}

	@Test
	@DisplayName("정상 주문을 생성하면 재고가 예약되고 주문 상태가 STOCK_RESERVED가 된다")
	void createOrder_success() {
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 3);

		OrderResponse response = orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());

		assertThat(response.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED.name());
		assertThat(response.getTotalAmount()).isEqualTo(3000L);
		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(3);
		assertThat(stock.availableQuantity()).isEqualTo(7);
	}

	@Test
	@DisplayName("가용 재고보다 많은 수량을 주문하면 예외가 발생한다")
	void createOrder_insufficientStock() {
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 11);
		long countBefore = orderRepository.count();

		Throwable thrown = catchThrowable(
				() -> orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString()));

		assertThat(thrown).isInstanceOf(CustomException.class);
		assertThat(orderRepository.count()).isEqualTo(countBefore);
	}

	@Test
	@DisplayName("같은 클라이언트 Idempotency-Key로 더블클릭해도 주문은 한 건만 생성되고, 두 번째 호출은 같은 응답을 재사용한다")
	void createOrder_sameClientKeyTwice_reusesCachedResult() {
		String clientKey = UUID.randomUUID().toString();
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 1);

		OrderResponse first = orderService.createOrder(buyer.getId(), request, clientKey);
		OrderResponse second = orderService.createOrder(buyer.getId(), request, clientKey);

		assertThat(second.getOrderNumber()).isEqualTo(first.getOrderNumber());
		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(1);
	}

	@Test
	@DisplayName("다른 클라이언트 Idempotency-Key면 같은 내용이라도 각각 독립된 주문으로 생성된다")
	void createOrder_differentClientKeys_createIndependentOrders() {
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 1);

		OrderResponse first = orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());
		OrderResponse second = orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString());

		assertThat(first.getOrderNumber()).isNotEqualTo(second.getOrderNumber());
		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(2);
	}

	@Test
	@DisplayName("서로 다른 회원이 같은 Idempotency-Key를 보내도 각자 독립된 주문으로 생성된다")
	void createOrder_sameKeyDifferentMembers_createIndependentOrders() {
		Member otherBuyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("다른 구매자").role(MemberRole.USER).build());
		String sharedKey = UUID.randomUUID().toString();
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 1);

		OrderResponse first = orderService.createOrder(buyer.getId(), request, sharedKey);
		OrderResponse second = orderService.createOrder(otherBuyer.getId(), request, sharedKey);

		assertThat(first.getOrderNumber()).isNotEqualTo(second.getOrderNumber());
		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(2);
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
