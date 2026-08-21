package pocketpaystore.pocketpay_core.settlement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ProductErrorCode;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.product.domain.Product;
import pocketpaystore.pocketpay_core.product.repository.ProductRepository;
import pocketpaystore.pocketpay_core.settlement.domain.Settlement;
import pocketpaystore.pocketpay_core.settlement.repository.SettlementRepository;

@Service
@RequiredArgsConstructor
public class SettlementService {

	private final SettlementRepository settlementRepository;
	private final PaymentRepository paymentRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;

	@Value("${settlement.pg-fee-rate}")
	private double pgFeeRate;

	@Value("${settlement.platform-fee-rate}")
	private double platformFeeRate;

	@Transactional
	public void create(Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		OrderItem item = orderItemRepository.findByOrderId(payment.getOrderId())
				.orElseThrow(() -> new CustomException(OrderErrorCode.EMPTY_ORDER_ITEMS));
		Product product = productRepository.findById(item.getProductId())
				.orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));

		long amount = payment.getAmount();
		long pgFeeAmount = Math.round(amount * pgFeeRate);
		long platformFeeAmount = Math.round(amount * platformFeeRate);
		long netAmount = amount - pgFeeAmount - platformFeeAmount;

		Settlement settlement = Settlement.create(
			paymentId, product.getVendorId(), amount, pgFeeAmount, platformFeeAmount, netAmount);
		settlementRepository.save(settlement);
	}

}
