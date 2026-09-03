package pocketpaystore.pocketpay_core.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;

class StockTest {

	@Test
	@DisplayName("여러 주문의 예약 재고 중 현재 주문 수량만 판매 확정할 수 있다")
	void confirmPartOfReservedQuantity() {
		Stock stock = stockWithReservedQuantity(10);

		stock.confirm(1);

		assertThat(stock.getReservedQuantity()).isEqualTo(9);
		assertThat(stock.getSoldQuantity()).isEqualTo(1);
	}

	@Test
	@DisplayName("여러 주문의 예약 재고 중 현재 주문 수량만 예약 해제할 수 있다")
	void releasePartOfReservedQuantity() {
		Stock stock = stockWithReservedQuantity(10);

		stock.release(1);

		assertThat(stock.getReservedQuantity()).isEqualTo(9);
		assertThat(stock.getSoldQuantity()).isZero();
	}

	@Test
	@DisplayName("예약 수량보다 많은 수량은 판매 확정할 수 없다")
	void cannotConfirmMoreThanReservedQuantity() {
		Stock stock = stockWithReservedQuantity(1);

		assertThatThrownBy(() -> stock.confirm(2))
				.isInstanceOf(CustomException.class)
				.satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
						.isEqualTo(ProductErrorCode.INSUFFICIENT_RESERVED_QUANTITY));
	}

	private Stock stockWithReservedQuantity(int reservedQuantity) {
		return Stock.builder()
				.productId(1L)
				.totalQuantity(100)
				.reservedQuantity(reservedQuantity)
				.soldQuantity(0)
				.build();
	}
}
