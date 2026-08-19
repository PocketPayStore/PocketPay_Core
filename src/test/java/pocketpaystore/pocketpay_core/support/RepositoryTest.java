package pocketpaystore.pocketpay_core.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import pocketpaystore.pocketpay_core.common.config.JpaAuditingConfig;
import pocketpaystore.pocketpay_core.common.config.QuerydslConfig;

@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
public abstract class RepositoryTest {
}
