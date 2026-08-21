package pocketpaystore.pocketpay_core.vendor.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pocketpaystore.pocketpay_core.vendor.domain.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

}
