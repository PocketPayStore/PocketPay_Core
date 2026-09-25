package pocketpaystore.pocketpay_core.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.member.domain.Member;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;
import pocketpaystore.pocketpay_core.member.repository.MemberRepository;
import pocketpaystore.pocketpay_core.point.domain.PointBalance;
import pocketpaystore.pocketpay_core.point.domain.PointEarnLog;
import pocketpaystore.pocketpay_core.point.domain.PointEarnStatus;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;
import pocketpaystore.pocketpay_core.point.repository.PointEarnLogRepository;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PointEarnApplyServiceTest extends RedisTestContainer {

	@Autowired
	private PointEarnApplyService pointEarnApplyService;

	@Autowired
	private PointEarnLogRepository pointEarnLogRepository;

	@Autowired
	private PointBalanceRepository pointBalanceRepository;

	@Autowired
	private MemberRepository memberRepository;

	private Long memberId;

	@BeforeEach
	void setUp() {
		Member member = memberRepository.save(
				Member.builder().email("earn-" + UUID.randomUUID() + "@test.com")
						.password("test1234").name("적립테스트").role(MemberRole.USER).build());
		memberId = member.getId();
		pointBalanceRepository.save(PointBalance.create(memberId));
	}

	@Test
	@DisplayName("PENDING 상태의 적립 로그를 적용하면 잔액이 올라가고 로그는 RESOLVED가 된다")
	void apply_pendingLog_earnsPointsAndResolves() {
		Long logId = savePointEarnLog(100L);

		boolean applied = pointEarnApplyService.apply(logId);

		assertThat(applied).isTrue();
		PointBalance balance = pointBalanceRepository.findByMemberId(memberId).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(100L);
		PointEarnLog earnLog = pointEarnLogRepository.findById(logId).orElseThrow();
		assertThat(earnLog.getStatus()).isEqualTo(PointEarnStatus.RESOLVED);
		assertThat(earnLog.getResolvedAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 RESOLVED된 로그를 다시 적용하면 잔액이 중복 반영되지 않는다")
	void apply_alreadyResolvedLog_isSkipped() {
		Long logId = savePointEarnLog(100L);
		pointEarnApplyService.apply(logId);

		boolean appliedAgain = pointEarnApplyService.apply(logId);

		assertThat(appliedAgain).isFalse();
		PointBalance balance = pointBalanceRepository.findByMemberId(memberId).orElseThrow();
		assertThat(balance.getBalance()).isEqualTo(100L);
	}

	@Test
	@DisplayName("존재하지 않는 로그 id를 적용하려 하면 예외가 발생한다")
	void apply_missingLog_throws() {
		assertThatThrownBy(() -> pointEarnApplyService.apply(999_999L))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PointErrorCode.POINT_EARN_LOG_NOT_FOUND);
	}

	private Long savePointEarnLog(long amount) {
		PointEarnLog earnLog = pointEarnLogRepository.save(PointEarnLog.create(memberId, 1L, 1L, amount));
		return earnLog.getId();
	}

}
