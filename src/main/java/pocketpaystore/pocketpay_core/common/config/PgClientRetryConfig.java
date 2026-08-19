package pocketpaystore.pocketpay_core.common.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;

@Configuration
public class PgClientRetryConfig {

	private static final Duration INITIAL_INTERVAL = Duration.ofMillis(200);
	private static final double BACKOFF_MULTIPLIER = 2.0;
	private static final double RANDOMIZATION_FACTOR = 0.5;

	@Bean
	public RetryConfigCustomizer pgClientRetryConfigCustomizer() {
		return RetryConfigCustomizer.of("pgClient",
				builder -> builder
						.retryOnException(this::isRetryable)
						.intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
								INITIAL_INTERVAL, BACKOFF_MULTIPLIER, RANDOMIZATION_FACTOR)));
	}

	private boolean isRetryable(Object throwable) {
		return isRetryable((Throwable) throwable);
	}

	private boolean isRetryable(Throwable throwable) {
		if (throwable instanceof FeignException feignException) {
			int status = feignException.status();
			return status >= 500 || status == -1;
		}
		return throwable instanceof IOException;
	}

}
