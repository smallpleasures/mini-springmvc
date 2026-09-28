
package com.yrainy.web.servlet.mvc.condition;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * {@link RequestCondition} 的基类，实现 {@link #equals(Object)}、{@link #hashCode()} 和 {@link #toString()}。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 * @param <T> 可与此 RequestCondition 合并和比较的类型
 */
public abstract class AbstractRequestCondition<T extends AbstractRequestCondition<T>> implements RequestCondition<T> {

	/**
	 * 判断此条件是否为空，即是否不包含任何条件项。
	 * @return 为空时返回 {@code true}，否则返回 {@code false}
	 */
	public boolean isEmpty() {
		return getContent().isEmpty();
	}

	/**
	 * 返回组成请求条件的各个条件项，例如 URL 模式、HTTP 方法或参数表达式。
	 * @return 条件项集合，不会返回 {@code null}
	 */
	protected abstract Collection<?> getContent();

	/**
	 * 返回打印条件项时使用的分隔符，例如 URL 模式使用 {@code " || "}，参数表达式使用 {@code " && "}。
	 */
	protected abstract String getToStringInfix();


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return getContent().equals(((AbstractRequestCondition<?>) other).getContent());
	}

	@Override
	public int hashCode() {
		return getContent().hashCode();
	}

	@Override
	public String toString() {
		String infix = getToStringInfix();
		StringJoiner joiner = new StringJoiner(infix, "[", "]");
		for (Object expression : getContent()) {
			joiner.add(expression.toString());
		}
		return joiner.toString();
	}

}
