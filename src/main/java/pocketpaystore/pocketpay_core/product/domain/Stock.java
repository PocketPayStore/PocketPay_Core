package pocketpaystore.pocketpay_core.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.common.BaseEntity;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;

@Getter
@Entity
@Table(name = "stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Stock extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false, unique = true)
	private Long productId;

	@Column(name = "total_quantity", nullable = false)
	private int totalQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	private int reservedQuantity;

	@Column(name = "sold_quantity", nullable = false)
	private int soldQuantity;

	@Version
	@Column(nullable = false)
	private Long version;

	public int availableQuantity() {
		return totalQuantity - reservedQuantity - soldQuantity;
	}

	public void reserve(int quantity) {
		if (quantity <= 0) {
			throw new CustomException(ProductErrorCode.INVALID_QUANTITY);
		}
		if (availableQuantity() < quantity) {
			throw new CustomException(ProductErrorCode.INSUFFICIENT_STOCK);
		}
		this.reservedQuantity += quantity;
	}

	public void confirm(int quantity) {
		if (this.reservedQuantity != quantity) {
			throw new CustomException(ProductErrorCode.INSUFFICIENT_RESERVED_QUANTITY);
		}
		this.reservedQuantity -= quantity;
		this.soldQuantity += quantity;
	}

	public void release(int quantity) {
		if (this.reservedQuantity != quantity) {
			throw new CustomException(ProductErrorCode.INSUFFICIENT_RESERVED_QUANTITY);
		}
		this.reservedQuantity -= quantity;
	}

}
