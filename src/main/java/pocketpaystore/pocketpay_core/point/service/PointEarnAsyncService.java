package pocketpaystore.pocketpay_core.point.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointEarnAsyncService {

	private final PointEarnApplyService pointEarnApplyService;
	private final PointEarnLogService pointEarnLogService;

	@Async("pointEarnTaskExecutor")
	public void applyAsync(Long id) {
		try {
			pointEarnApplyService.apply(id);
		} catch (Exception e) {
			log.error("[PointEarn] 포인트 적립 실패, 배치 재시도 대상으로 남김: id={}", id, e);
			pointEarnLogService.markFailed(id);
		}
	}

}
