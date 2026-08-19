package pocketpaystore.pocketpay_core.notification.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

	public void notify(Long paymentId) {
		log.info("[Notification] 결제 완료 알림 발송(스텁): paymentId={}", paymentId);
	}

}
