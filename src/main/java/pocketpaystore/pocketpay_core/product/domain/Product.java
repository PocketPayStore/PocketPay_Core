package pocketpaystore.pocketpay_core.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.common.BaseEntity;

/** 위탁판매 상품. 회원이 자기 카드를 등록해서 파는 구조라 seller_id가 필수다. */
@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Product extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "seller_id", nullable = false)
	private Long sellerId;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false)
	private Long price;

}
