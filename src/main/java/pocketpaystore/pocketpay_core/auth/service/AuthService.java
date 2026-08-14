package pocketpaystore.pocketpay_core.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.auth.dto.request.LoginRequest;
import pocketpaystore.pocketpay_core.auth.dto.response.LoginResponse;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.AuthErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberRepository memberRepository;
	private final JwtProvider jwtProvider;

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		Member member = memberRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new CustomException(AuthErrorCode.MEMBER_NOT_FOUND));

		if (!member.getPassword().equals(request.getPassword())) {
			throw new CustomException(AuthErrorCode.INVALID_PASSWORD);
		}

		return new LoginResponse(jwtProvider.generate(member.getId(), member.getRole()));
	}

}
