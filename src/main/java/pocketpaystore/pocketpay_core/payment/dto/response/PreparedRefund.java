package pocketpaystore.pocketpay_core.payment.dto.response;

import lombok.Builder;
import lombok.Getter;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.Refund;

@Getter
@Builder
public class PreparedRefund {

	private Refund refund;
	private Payment payment;

}
