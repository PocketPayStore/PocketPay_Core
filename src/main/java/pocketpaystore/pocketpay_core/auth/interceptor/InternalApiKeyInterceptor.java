package pocketpaystore.pocketpay_core.auth.interceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalApiKeyInterceptor implements HandlerInterceptor {

	private static final String HEADER = "X-Internal-Api-Key";
	private final byte[] expectedKey;

	public InternalApiKeyInterceptor(@Value("${internal-api.key}") String expectedKey) {
		this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		String provided = request.getHeader(HEADER);
		if (provided == null || !MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		return true;
	}
}
