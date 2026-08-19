package pocketpaystore.pocketpay_core.point.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.point.domain.PointLedger;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {
}
