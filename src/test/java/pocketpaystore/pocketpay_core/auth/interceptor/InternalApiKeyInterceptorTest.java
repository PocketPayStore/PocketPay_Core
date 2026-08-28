package pocketpaystore.pocketpay_core.auth.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class InternalApiKeyInterceptorTest {

	private final InternalApiKeyInterceptor interceptor = new InternalApiKeyInterceptor("secret");

	@Test
	void acceptsMatchingKey() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("X-Internal-Api-Key")).thenReturn("secret");

		assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
	}

	@Test
	void rejectsMissingKey() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
		verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}
}
