package pocketpaystore.pocketpay_core.auth.resolver;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;

@Getter
@AllArgsConstructor
public class LoginMember {

	private Long memberId;
	private MemberRole role;

}
