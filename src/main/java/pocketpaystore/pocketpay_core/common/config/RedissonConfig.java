package pocketpaystore.pocketpay_core.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import io.lettuce.core.api.StatefulConnection;

import java.time.Duration;

@Configuration
public class RedissonConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Value("${spring.data.redis.ssl.enabled}")
	private boolean sslEnabled;

	@Value("${redis.clients.idempotency.max-active}")
	private int idempotencyMaxActive;

	@Value("${redis.clients.payment-event.max-active}")
	private int paymentEventMaxActive;

	@Value("${redis.clients.max-idle}")
	private int maxIdle;

	@Value("${redis.clients.min-idle}")
	private int minIdle;

	@Value("${redis.clients.max-wait}")
	private Duration maxWait;

	@Bean(name = {"idempotencyRedisConnectionFactory", "redisConnectionFactory"})
	@Primary
	public RedisConnectionFactory idempotencyRedisConnectionFactory() {
		return createPooledConnectionFactory(idempotencyMaxActive);
	}

	@Bean("paymentEventRedisConnectionFactory")
	public RedisConnectionFactory paymentEventRedisConnectionFactory() {
		return createPooledConnectionFactory(paymentEventMaxActive);
	}

	@Bean("idempotencyRedisTemplate")
	@Primary
	public StringRedisTemplate idempotencyRedisTemplate(
			@Qualifier("idempotencyRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

	@Bean("paymentEventRedisTemplate")
	public StringRedisTemplate paymentEventRedisTemplate(
			@Qualifier("paymentEventRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

	private RedisConnectionFactory createPooledConnectionFactory(int maxActive) {
		RedisStandaloneConfiguration standaloneConfig =
			new RedisStandaloneConfiguration(redisHost, redisPort);

		GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
		poolConfig.setMaxTotal(maxActive);
		poolConfig.setMaxIdle(Math.min(maxIdle, maxActive));
		poolConfig.setMinIdle(Math.min(minIdle, maxActive));
		poolConfig.setMaxWait(maxWait);

		LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
			LettucePoolingClientConfiguration.builder()
				.commandTimeout(Duration.ofSeconds(2))
				.poolConfig(poolConfig);

		if (sslEnabled) {
			builder.useSsl();
		}

		LettuceConnectionFactory connectionFactory =
				new LettuceConnectionFactory(standaloneConfig, builder.build());
		connectionFactory.setShareNativeConnection(false);
		return connectionFactory;
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
