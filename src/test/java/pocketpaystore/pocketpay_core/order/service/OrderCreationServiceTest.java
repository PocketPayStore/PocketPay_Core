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
import pocketpaystore.pocketpay_core.order.dto.request.CreateOrderRequest;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.domain.Stock;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.product.repository.StockRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;
import pocketpaystore.pocketpay_core.vendor.domain.Vendor;
import pocketpaystore.pocketpay_core.vendor.repository.VendorRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderCreationServiceTest extends RedisTestContainer {

	@Autowired
	private OrderCreationService orderCreationService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private OrderRepository orderRepository;

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
	@DisplayName("재고 예약이 실패하면 Order 저장을 시도하지 않는다")
	void create_insufficientStock_neverPersists() {
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 100);
		long countBefore = orderRepository.count();

		Throwable thrown = catchThrowable(
				() -> orderCreationService.create(buyer.getId(), request, UUID.randomUUID().toString()));

		assertThat(thrown).isInstanceOf(CustomException.class);
		assertThat(orderRepository.count()).isEqualTo(countBefore);
	}

	@Test
	@DisplayName("중복 멱등키로 주문 저장이 실패하면 같은 트랜잭션의 재고 선점도 롤백된다")
	void create_duplicateKey_rollsBackReservedStock() {
		CreateOrderRequest request = new CreateOrderRequest(product.getId(), 3);
		String idempotencyKey = UUID.randomUUID().toString();
		orderCreationService.create(buyer.getId(), request, idempotencyKey);

		Throwable thrown = catchThrowable(
				() -> orderCreationService.create(buyer.getId(), request, idempotencyKey));

		assertThat(thrown).isInstanceOf(CustomException.class);
		assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(CommonErrorCode.DUPLICATE_REQUEST);
		Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
		assertThat(stock.getReservedQuantity()).isEqualTo(3);
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

}
