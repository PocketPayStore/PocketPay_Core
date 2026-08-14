package pocketpaystore.pocketpay_core.auth.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.auth.resolver.MemberArgumentResolver;
import pocketpaystore.pocketpay_core.auth.service.JwtProvider;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.AuthErrorCode;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

	private static final String CLAIM_ROLE = "role";

	private final JwtProvider jwtProvider;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String token = resolveToken(request);
		if (token == null || !jwtProvider.validateToken(token)) {
			throw new CustomException(AuthErrorCode.INVALID_TOKEN);
		}

		Claims claims = jwtProvider.parseToken(token);
		request.setAttribute(MemberArgumentResolver.MEMBER_ID_ATTR, Long.valueOf(claims.getSubject()));
		request.setAttribute(MemberArgumentResolver.MEMBER_ROLE_ATTR, MemberRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
		return true;
	}

	private String resolveToken(HttpServletRequest request) {
		String bearer = request.getHeader("Authorization");
		if (bearer != null && bearer.startsWith("Bearer ")) {
			return bearer.substring(7);
		}
		return null;
	}

}
