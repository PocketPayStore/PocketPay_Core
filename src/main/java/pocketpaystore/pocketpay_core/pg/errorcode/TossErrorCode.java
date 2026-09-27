package pocketpaystore.pocketpay_core.pg.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TossErrorCode {

	REJECT_CARD_PAYMENT("한도초과 혹은 잔액부족으로 결제에 실패했습니다.", true),
	REJECT_ACCOUNT_PAYMENT("잔액부족으로 결제에 실패했습니다.", true),
	REJECT_CARD_COMPANY("카드사에서 결제 승인을 거절했습니다.", true),
	INVALID_STOPPED_CARD("정지된 카드입니다.", true),
	INVALID_REJECT_CARD("카드 사용이 거절되었습니다. 카드사 문의가 필요합니다.", true),
	INVALID_CARD_EXPIRATION("카드 유효기간을 다시 확인해주세요.", true),
	INVALID_CARD_NUMBER("카드번호를 다시 확인해주세요.", true),
	INVALID_CARD_LOST_OR_STOLEN("분실 혹은 도난 카드입니다.", true),
	EXCEED_MAX_AUTH_COUNT("최대 인증 횟수를 초과했습니다.", true),
	INVALID_PASSWORD("결제 비밀번호가 일치하지 않습니다.", true),
	NOT_ALLOWED_POINT_USE("포인트 사용이 불가한 카드입니다.", true),
	BELOW_MINIMUM_AMOUNT("최소 결제금액 미달입니다.", true),

	INVALID_API_KEY("잘못된 시크릿키 연동 정보입니다.", false),
	UNAUTHORIZED_KEY("인증되지 않은 시크릿 키 혹은 클라이언트 키입니다.", false),
	INVALID_REQUEST("잘못된 요청입니다.", false),
	PROVIDER_ERROR("일시적인 오류가 발생했습니다.", false),
	ALREADY_PROCESSED_PAYMENT("이미 처리된 결제입니다.", false),
	ALREADY_PROCESSING_REQUEST("이미 처리 중인 요청입니다.", false),
	FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING("결제가 완료되지 않았습니다.", false),
	FAILED_INTERNAL_SYSTEM_PROCESSING("내부 시스템 처리 작업이 실패했습니다.", false),
	CARD_PROCESSING_ERROR("카드사에서 오류가 발생했습니다.", false),
	UNKNOWN_PAYMENT_ERROR("결제에 실패했습니다.", false);

	private final String description;
	private final boolean customerFault;

	public static boolean isCustomerFault(String code) {
		if (code == null) {
			return false;
		}
		try {
			return TossErrorCode.valueOf(code).isCustomerFault();
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

}
