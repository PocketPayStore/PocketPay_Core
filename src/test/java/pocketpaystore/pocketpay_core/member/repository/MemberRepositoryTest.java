package pocketpaystore.pocketpay_core.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.support.RepositoryTest;

class MemberRepositoryTest extends RepositoryTest {

	@Autowired
	private MemberRepository memberRepository;

	@Test
	@DisplayName("이메일로 회원을 조회하면 저장된 회원을 반환한다")
	void findByEmail_success() {
		Member member = Member.builder()
				.email("test@test.com")
				.password("test1234")
				.name("테스트유저")
				.role(MemberRole.USER)
				.build();
		memberRepository.save(member);

		Optional<Member> found = memberRepository.findByEmail("test@test.com");

		assertThat(found).isPresent();
		assertThat(found.get().getEmail()).isEqualTo("test@test.com");
		assertThat(found.get().getRole()).isEqualTo(MemberRole.USER);
	}

	@Test
	@DisplayName("존재하지 않는 이메일로 조회하면 빈 값을 반환한다")
	void findByEmail_notFound() {

		Optional<Member> found = memberRepository.findByEmail("nobody@test.com");

		assertThat(found).isEmpty();
	}

}
