package pocketpaystore.pocketpay_core.common.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Request;

@Configuration
@EnableFeignClients(basePackages = "pocketpaystore.pocketpay_core")
public class FeignConfig {

	@Bean
	public Request.Options feignRequestOptions() {
		return new Request.Options(2000, TimeUnit.MILLISECONDS, 3000, TimeUnit.MILLISECONDS, true);
	}

}
