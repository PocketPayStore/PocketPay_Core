package pocketpaystore.pocketpay_core.product.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

	private final Long id;
	private final String name;
	private final Long price;
	private final int availableQuantity;

}
