package pocketpaystore.pocketpay_core.common.exception;

import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;

public class OrderExpiredException extends CustomException {

	public OrderExpiredException() {
		super(OrderErrorCode.ORDER_EXPIRED);
	}

}