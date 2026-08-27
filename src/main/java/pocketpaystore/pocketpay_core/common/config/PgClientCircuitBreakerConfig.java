package pocketpaystore.pocketpay_core.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;

@Configuration
public class PgClientCircuitBreakerConfig {

	@Bean
	public CircuitBreakerConfigCustomizer pgClientCircuitBreakerConfigCustomizer() {
		return CircuitBreakerConfigCustomizer.of("pgClient",
				builder -> builder.ignoreException(this::isUserFault));
	}

	private boolean isUserFault(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof FeignException feignException) {
				int status = feignException.status();
				return status >= 400 && status < 500;
			}
			current = current.getCause();
		}
		return false;
	}
}
