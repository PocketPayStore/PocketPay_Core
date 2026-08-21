package pocketpaystore.pocketpay_core.common.lock;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/** {@link DistributedLock#key()}에 적은 SpEL 식을 실제 메서드 인자 값으로 풀어준다. */
final class CustomSpringELParser {

	private CustomSpringELParser() {
	}

	static Object getDynamicValue(String[] parameterNames, Object[] args, String key) {
		ExpressionParser parser = new SpelExpressionParser();
		StandardEvaluationContext context = new StandardEvaluationContext();

		for (int i = 0; i < parameterNames.length; i++) {
			context.setVariable(parameterNames[i], args[i]);
		}

		return parser.parseExpression(key).getValue(context, Object.class);
	}

}
