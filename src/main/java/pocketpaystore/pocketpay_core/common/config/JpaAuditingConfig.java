package pocketpaystore.pocketpay_core.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity의 createdAt/updatedAt(@CreatedDate/@LastModifiedDate)이 동작하려면
 * JPA Auditing이 활성화되어 있어야 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
