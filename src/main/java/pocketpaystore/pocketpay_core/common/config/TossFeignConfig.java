package pocketpaystore.pocketpay_core.common.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

/**
 * PgClient(FeignClient) 전용 설정. {@code @FeignClient(configuration = ...)}로만 연결되고
 * 앱의 일반 컴포넌트 스캔 대상이 아니므로 {@code @Configuration}을 붙이지 않는다 — 붙이면
 * 이 인증 헤더가 다른 Feign 클라이언트에도 전역으로 적용돼버린다.
 */
public class TossFeignConfig {

	@Value("${toss.secret-key}")
	private String secretKey;

	@Bean
	public RequestInterceptor tossAuthInterceptor() {
		return requestTemplate -> {
			String credentials = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
			requestTemplate.header("Authorization", "Basic " + credentials);
		};
	}

}
