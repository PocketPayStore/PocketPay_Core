package pocketpaystore.pocketpay_core.saga.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.saga.domain.SagaLog;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long> {

	List<SagaLog> findByOrderId(Long orderId);

}
