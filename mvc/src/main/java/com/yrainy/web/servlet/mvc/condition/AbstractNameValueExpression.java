package com.yrainy.web.servlet.mvc.condition;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/** 解析并匹配 {@code name}, {@code name=value} 和 {@code name!=value} 条件。 */
abstract class AbstractNameValueExpression<T> implements NameValueExpression<T> {

	private final String name;
	private final T value;
	private final boolean negated;

	AbstractNameValueExpression(String expression) {
		String token = expression.trim();
		boolean isNegated = token.startsWith("!");
		if (isNegated) {
			token = token.substring(1).trim();
		}
		int separator = token.indexOf("!=");
		if (separator >= 0) {
			isNegated = true;
		}
		else {
			separator = token.indexOf('=');
		}
		this.name = (separator >= 0 ? token.substring(0, separator) : token).trim();
		if (this.name.isEmpty()) {
			throw new IllegalArgumentException("Request condition name must not be empty: " + expression);
		}
		this.value = (separator >= 0 ? parseValue(token.substring(separator + (token.charAt(separator) == '!' ? 2 : 1)).trim()) : null);
		this.negated = isNegated;
	}

	AbstractNameValueExpression(String name, T value, boolean negated) {
		this.name = name;
		this.value = value;
		this.negated = negated;
	}
	protected abstract T parseValue(String value);

	protected abstract boolean isNamePresent(HttpServletRequest request);

	protected abstract boolean matchValue(HttpServletRequest request, T value);

	public boolean match(HttpServletRequest request) {
		boolean matched = (this.value == null ? isNamePresent(request) :
				isNamePresent(request) && matchValue(request, this.value));
		return (this.negated != matched);
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public T getValue() {
		return this.value;
	}

	@Override
	public boolean isNegated() {
		return this.negated;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		AbstractNameValueExpression<?> that = (AbstractNameValueExpression<?>) other;
		return this.negated == that.negated && this.name.equals(that.name) && Objects.equals(this.value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.value, this.negated);
	}

	@Override
	public String toString() {
		return this.name + (this.value != null ? (this.negated ? "!=" : "=") + this.value : (this.negated ? "!" : ""));
	}
}
