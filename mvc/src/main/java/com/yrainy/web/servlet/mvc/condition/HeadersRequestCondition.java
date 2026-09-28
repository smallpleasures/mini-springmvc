package com.yrainy.web.servlet.mvc.condition;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/** 匹配 {@code @RequestMapping} 中的请求头条件。 */
public final class HeadersRequestCondition extends AbstractRequestCondition<HeadersRequestCondition> {

	private final Set<NameValueExpression<String>> expressions;

	public HeadersRequestCondition(String... expressions) {
		Set<NameValueExpression<String>> parsed = new LinkedHashSet<>();
		for (String expression : expressions) {
			parsed.add(new HeaderExpression(expression));
		}
		this.expressions = Collections.unmodifiableSet(parsed);
	}

	private HeadersRequestCondition(Set<NameValueExpression<String>> expressions) {
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
	public HeadersRequestCondition combine(HeadersRequestCondition other) {
		Set<NameValueExpression<String>> combined = new LinkedHashSet<>(this.expressions);
		combined.addAll(other.expressions);
		return new HeadersRequestCondition(combined);
	}

	@Override
	public HeadersRequestCondition getMatchingCondition(HttpServletRequest request) {
		for (NameValueExpression<String> expression : this.expressions) {
			if (!((HeaderExpression) expression).match(request)) {
				return null;
			}
		}
		return this;
	}

	@Override
	public int compareTo(HeadersRequestCondition other, HttpServletRequest request) {
		return Integer.compare(other.expressions.size(), this.expressions.size());
	}

	private static final class HeaderExpression extends AbstractNameValueExpression<String> {
		private HeaderExpression(String expression) {
			super(expression);
		}

		@Override
		protected String parseValue(String value) {
			return value;
		}

		@Override
		protected boolean isNamePresent(HttpServletRequest request) {
			return request.getHeader(getName()) != null;
		}

		@Override
		protected boolean matchValue(HttpServletRequest request, String value) {
			return value.equals(request.getHeader(getName()));
		}
	}
}
