package pocketpaystore.pocketpay_core.point.event.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

import pocketpaystore.pocketpay_core.point.event.model.PointEarnRequestedEvent;
import pocketpaystore.pocketpay_core.point.service.PointEarnAsyncService;

class PointEarnRequestedEventListenerTest {

	@Test
	void onPointEarnRequested_threadPoolRejects_doesNotPropagateException() {
		PointEarnAsyncService pointEarnAsyncService = mock(PointEarnAsyncService.class);
		doThrow(new RejectedExecutionException("pool full")).when(pointEarnAsyncService).applyAsync(any());
		PointEarnRequestedEventListener listener = new PointEarnRequestedEventListener(pointEarnAsyncService);

		assertThatCode(() -> listener.onPointEarnRequested(new PointEarnRequestedEvent(1L)))
				.doesNotThrowAnyException();

		verify(pointEarnAsyncService).applyAsync(1L);
	}

}
