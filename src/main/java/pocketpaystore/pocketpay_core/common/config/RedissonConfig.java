package pocketpaystore.pocketpay_core.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

// redisson-spring-boot-starter가 자동 등록하는 RedisConnectionFactory(RedissonConnectionFactory)를
// 쓰면 IdempotencyKeyGuard의 StringRedisTemplate이 그걸 물게 되는데, 이 Spring Data Redis
// 버전에선 그 구현체가 DefaultedRedisConnection의 기본 메서드를 다 못 채워 set() 호출 시
// StackOverflowError가 난다. 그래서 Lettuce 팩토리를 직접 등록해 자동설정을 양보시킨다.
@Configuration
public class RedissonConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Bean
	@Primary
	public RedisConnectionFactory redisConnectionFactory() {
		return new LettuceConnectionFactory(redisHost, redisPort);
	}

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://" + redisHost + ":" + redisPort);
		return Redisson.create(config);
	}

}
