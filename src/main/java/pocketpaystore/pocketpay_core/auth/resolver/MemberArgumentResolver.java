package pocketpaystore.pocketpay_core.auth.resolver;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;

@Component
public class MemberArgumentResolver implements HandlerMethodArgumentResolver {

	public static final String MEMBER_ID_ATTR = "memberId";
	public static final String MEMBER_ROLE_ATTR = "memberRole";

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(AuthMember.class)
				&& parameter.getParameterType().equals(LoginMember.class);
	}

	@Override
	public LoginMember resolveArgument(MethodParameter parameter,
										ModelAndViewContainer mavContainer,
										NativeWebRequest webRequest,
										WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Long memberId = (Long) request.getAttribute(MEMBER_ID_ATTR);
		MemberRole role = (MemberRole) request.getAttribute(MEMBER_ROLE_ATTR);
		return new LoginMember(memberId, role);
	}

}
