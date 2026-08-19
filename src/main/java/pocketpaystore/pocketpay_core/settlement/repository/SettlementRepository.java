package pocketpaystore.pocketpay_core.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.settlement.domain.Settlement;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
}
