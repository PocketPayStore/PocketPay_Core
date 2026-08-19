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
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.order.domain.OrderStatus;
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.dto.response.OrderResponse;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderServiceTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	private Member buyer;
	private Product product;

	@BeforeEach
	void setUp() {
		buyer = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("구매자").role(MemberRole.USER).build());
		Member seller = memberRepository.save(
				Member.builder().email(uniqueEmail()).password("test1234").name("판매자").role(MemberRole.USER).build());

		product = productRepository.save(
				Product.builder().sellerId(seller.getId()).name("이상해씨 카드").price(1000L).build());
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

		Throwable thrown = catchThrowable(
				() -> orderService.createOrder(buyer.getId(), request, UUID.randomUUID().toString()));

		assertThat(thrown).isInstanceOf(CustomException.class);
	}

	@Test
	@DisplayName("동일한 Idempotency-Key로 재요청하면 중복 요청 예외가 발생한다")
	void createOrder_duplicateIdempotencyKey() {
		String idempotencyKey = UUID.randomUUID().toString();
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 1);
		orderService.createOrder(buyer.getId(), request, idempotencyKey);

		Throwable thrown = catchThrowable(() -> orderService.createOrder(buyer.getId(), request, idempotencyKey));

		assertThat(thrown).isInstanceOf(CustomException.class);
		assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(CommonErrorCode.DUPLICATE_REQUEST);
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
