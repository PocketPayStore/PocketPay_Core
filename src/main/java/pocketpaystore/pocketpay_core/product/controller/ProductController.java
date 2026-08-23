package pocketpaystore.pocketpay_core.product.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.auth.resolver.AuthMember;
import pocketpaystore.pocketpay_core.auth.resolver.LoginMember;
import pocketpaystore.pocketpay_core.common.response.ApiResponse;
import pocketpaystore.pocketpay_core.product.dto.response.ProductResponse;
import pocketpaystore.pocketpay_core.product.service.ProductQueryService;

// 요소 3a 부하테스트 보조용 조회 API. 화면용 상품 조회는 원래 이 레포 범위 밖(CLAUDE.md)이지만,
// 재고 예약 경로(쓰기·락)와 완전히 분리된 순수 조회 프로브가 필요해서 최소 스펙으로 추가했다.
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductQueryService productQueryService;

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
			@AuthMember LoginMember loginMember,
			@PathVariable Long id
	) {
		ProductResponse response = productQueryService.getProduct(id);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

}
