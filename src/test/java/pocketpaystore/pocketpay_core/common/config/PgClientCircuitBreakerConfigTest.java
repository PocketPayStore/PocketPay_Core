package pocketpaystore.pocketpay_core.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PgClientCircuitBreakerConfigTest extends RedisTestContainer {

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Autowired
	private MeterRegistry meterRegistry;

	private CircuitBreaker circuitBreaker;

	@BeforeEach
	void setUp() {
		circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgClient");
		circuitBreaker.reset();
	}

	@Test
	void pg4xxIsIgnored() {
		FeignException badRequest = feignException(400);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> {
				throw badRequest;
			})).isSameAs(badRequest);
		}

		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
	}

	@Test
	void circuitBreakerMetricsAreBound() {
		assertThat(meterRegistry.find("resilience4j.circuitbreaker.state")
				.tag("name", "pgClient")
				.gauges()).isNotEmpty();
	}

	@Test
	void systemFailureOpensAndSuccessfulProbesCloseCircuit() throws InterruptedException {
		FeignException serverError = feignException(500);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> {
				throw serverError;
			})).isSameAs(serverError);
		}

		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
		waitUntilHalfOpen(Duration.ofSeconds(2));

		assertThat(circuitBreaker.executeSupplier(() -> "probe-1")).isEqualTo("probe-1");
		assertThat(circuitBreaker.executeSupplier(() -> "probe-2")).isEqualTo("probe-2");
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	private FeignException feignException(int status) {
		FeignException exception = mock(FeignException.class);
		when(exception.status()).thenReturn(status);
		return exception;
	}

	private void waitUntilHalfOpen(Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN) {
				return;
			}
			Thread.sleep(20);
		}
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
	}
}
