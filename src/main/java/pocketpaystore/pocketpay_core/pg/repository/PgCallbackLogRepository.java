package pocketpaystore.pocketpay_core.pg.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.pg.domain.PgCallbackLog;

public interface PgCallbackLogRepository extends JpaRepository<PgCallbackLog, Long> {
}
