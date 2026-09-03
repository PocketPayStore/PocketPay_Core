package pocketpaystore.pocketpay_core.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PaymentEventAsyncConfig {

	@Bean("paymentEventTaskExecutor")
	public Executor paymentEventTaskExecutor(
			@Value("${payment-events.executor.core-pool-size}") int corePoolSize,
			@Value("${payment-events.executor.max-pool-size}") int maxPoolSize,
			@Value("${payment-events.executor.queue-capacity}") int queueCapacity) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("payment-event-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
		executor.initialize();
		return executor;
	}
}
