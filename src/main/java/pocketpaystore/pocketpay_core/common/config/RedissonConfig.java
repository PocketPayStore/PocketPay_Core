package pocketpaystore.pocketpay_core.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RedissonConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Value("${spring.data.redis.ssl.enabled:false}")
	private boolean sslEnabled;

	@Bean
	@Primary
	public RedisConnectionFactory redisConnectionFactory() {
		RedisStandaloneConfiguration standaloneConfig =
			new RedisStandaloneConfiguration(redisHost, redisPort);

		LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
			LettuceClientConfiguration.builder()
				.commandTimeout(Duration.ofSeconds(2));

		if (sslEnabled) {
			builder.useSsl();
		}

		return new LettuceConnectionFactory(standaloneConfig, builder.build());
	}

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		Config config = new Config();
		String protocol = sslEnabled ? "rediss://" : "redis://";
		config.useSingleServer()
			.setAddress(protocol + redisHost + ":" + redisPort);
		return Redisson.create(config);
	}

}