package pocketpaystore.pocketpay_core.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pocketpaystore.pocketpay_core.auth.dto.request.LoginRequest;
import pocketpaystore.pocketpay_core.auth.dto.response.LoginResponse;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.AuthErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRepository;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.support.ServiceTest;

class AuthServiceTest extends ServiceTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private MemberRepository memberRepository;

	@Test
	@DisplayName("이메일과 비밀번호가 일치하면 Access Token을 발급한다")
	void login_success() {
		// given
		Member member = Member.builder()
				.email("test@test.com")
				.password("test1234")
				.name("테스트유저")
				.role(MemberRole.USER)
				.build();
		memberRepository.save(member);
		LoginRequest request = new LoginRequest("test@test.com", "test1234");

		// when
		LoginResponse response = authService.login(request);

		// then
		assertThat(response.getAccessToken()).isNotBlank();
	}

	@Test
	@DisplayName("존재하지 않는 이메일로 로그인하면 예외가 발생한다")
	void login_memberNotFound() {
		// given
		LoginRequest request = new LoginRequest("nobody@test.com", "test1234");

		// when
		Throwable thrown = catchThrowable(() -> authService.login(request));

		// then
		assertThat(thrown).isInstanceOf(CustomException.class);
		assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	@DisplayName("비밀번호가 일치하지 않으면 예외가 발생한다")
	void login_invalidPassword() {
		// given
		Member member = Member.builder()
				.email("test@test.com")
				.password("test1234")
				.name("테스트유저")
				.role(MemberRole.USER)
				.build();
		memberRepository.save(member);
		LoginRequest request = new LoginRequest("test@test.com", "wrong-password");

		// when
		Throwable thrown = catchThrowable(() -> authService.login(request));

		// then
		assertThat(thrown).isInstanceOf(CustomException.class);
		assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.INVALID_PASSWORD);
	}

}
