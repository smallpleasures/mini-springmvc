package com.yrainy.web.servlet.mvc.condition;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/** 匹配 {@code @RequestMapping} 中的查询参数条件。 */
public final class ParamsRequestCondition extends AbstractRequestCondition<ParamsRequestCondition> {

	private final Set<NameValueExpression<String>> expressions;

	public ParamsRequestCondition(String... expressions) {
		Set<NameValueExpression<String>> parsed = new LinkedHashSet<>();
		for (String expression : expressions) {
			parsed.add(new ParamExpression(expression));
		}
		this.expressions = Collections.unmodifiableSet(parsed);
	}

	private ParamsRequestCondition(Set<NameValueExpression<String>> expressions) {
		this.expressions = Collections.unmodifiableSet(expressions);
	}

	public Set<NameValueExpression<String>> getExpressions() {
		return this.expressions;
	}

	@Override
	protected Collection<?> getContent() {
		return this.expressions;
	}

	@Override
	protected String getToStringInfix() {
		return " && ";
	}

	@Override
	public ParamsRequestCondition combine(ParamsRequestCondition other) {
		Set<NameValueExpression<String>> combined = new LinkedHashSet<>(this.expressions);
		combined.addAll(other.expressions);
		return new ParamsRequestCondition(combined);
	}

	@Override
	public ParamsRequestCondition getMatchingCondition(HttpServletRequest request) {
		for (NameValueExpression<String> expression : this.expressions) {
			if (!((ParamExpression) expression).match(request)) {
				return null;
			}
		}
		return this;
	}

	@Override
	public int compareTo(ParamsRequestCondition other, HttpServletRequest request) {
		return Integer.compare(other.expressions.size(), this.expressions.size());
	}

	private static final class ParamExpression extends AbstractNameValueExpression<String> {
		private ParamExpression(String expression) {
			super(expression);
		}

		@Override
		protected String parseValue(String value) {
			return value;
		}

		@Override
		protected boolean isNamePresent(HttpServletRequest request) {
			return request.getParameterMap().containsKey(getName());
		}

		@Override
		protected boolean matchValue(HttpServletRequest request, String value) {
			String[] values = request.getParameterValues(getName());
			return values != null && Arrays.asList(values).contains(value);
		}
	}
}
