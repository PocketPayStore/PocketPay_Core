package pocketpaystore.pocketpay_core.admin.resolver;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import pocketpaystore.pocketpay_core.auth.resolver.LoginMember;
import pocketpaystore.pocketpay_core.auth.resolver.MemberArgumentResolver;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.AuthErrorCode;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;

@Component
public class AdminArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(AdminMember.class)
				&& parameter.getParameterType().equals(LoginMember.class);
	}

	@Override
	public LoginMember resolveArgument(MethodParameter parameter,
										ModelAndViewContainer mavContainer,
										NativeWebRequest webRequest,
										WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Long memberId = (Long) request.getAttribute(MemberArgumentResolver.MEMBER_ID_ATTR);
		MemberRole role = (MemberRole) request.getAttribute(MemberArgumentResolver.MEMBER_ROLE_ATTR);
		if (role != MemberRole.ADMIN) {
			throw new CustomException(AuthErrorCode.FORBIDDEN);
		}
		return new LoginMember(memberId, role);
	}

}
