package pocketpaystore.pocketpay_core.point.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class PointEarnAsyncConfig {

	@Bean(name = "pointEarnTaskExecutor")
	public Executor pointEarnTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("point-earn-");
		executor.initialize();
		return executor;
	}

}
