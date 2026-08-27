package pocketpaystore.pocketpay_core.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;

@Configuration
public class PgClientCircuitBreakerConfig {

	@Bean
	public CircuitBreakerConfigCustomizer pgClientCircuitBreakerConfigCustomizer() {
		return CircuitBreakerConfigCustomizer.of("pgClient",
				builder -> builder.ignoreException(this::isUserFault));
	}

	@Bean
	public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry) {
		return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
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
