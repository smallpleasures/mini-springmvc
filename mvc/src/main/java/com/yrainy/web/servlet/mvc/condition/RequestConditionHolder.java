
package com.yrainy.web.servlet.mvc.condition;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;

/**
 * 用于包装 {@link RequestCondition} 的容器，适用于无法预先确定条件类型的情况，例如自定义条件。
 * <p>该类本身也实现 {@code RequestCondition}，可安全地处理不同条件的合并、比较和空值。
 *
 * <p>合并或比较两个容器时，要求其中的条件类型相同；类型不同时抛出 {@link ClassCastException}。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class RequestConditionHolder extends AbstractRequestCondition<RequestConditionHolder> {
	private final RequestCondition<Object> condition;


	/**
	 * 创建容器并包装给定请求条件。
	 * @param requestCondition 要包装的条件，可以为 {@code null}
	 */
	@SuppressWarnings("unchecked")
	public RequestConditionHolder(RequestCondition<?> requestCondition) {
		this.condition = (RequestCondition<Object>) requestCondition;
	}


	/**
	 * 返回容器中的请求条件；未包装条件时返回 {@code null}。
	 */
	public RequestCondition<?> getCondition() {
		return this.condition;
	}

	@Override
	protected Collection<?> getContent() {
		return (this.condition != null ? Collections.singleton(this.condition) : Collections.emptyList());
	}

	@Override
	protected String getToStringInfix() {
		return " ";
	}

	/**
	 * 检查两个容器中的条件类型后合并条件；其中一个容器为空时返回另一个容器。
	 */
	@Override
	public RequestConditionHolder combine(RequestConditionHolder other) {
		if (this.condition == null && other.condition == null) {
			return this;
		}
		else if (this.condition == null) {
			return other;
		}
		else if (other.condition == null) {
			return this;
		}
		else {
			assertEqualConditionTypes(this.condition, other.condition);
			RequestCondition<?> combined = (RequestCondition<?>) this.condition.combine(other.condition);
			return new RequestConditionHolder(combined);
		}
	}

	/**
	 * 确认两个容器中的请求条件类型相同。
	 */
	private void assertEqualConditionTypes(RequestCondition<?> thisCondition, RequestCondition<?> otherCondition) {
		Class<?> clazz = thisCondition.getClass();
		Class<?> otherClazz = otherCondition.getClass();
		if (!clazz.equals(otherClazz)) {
			throw new ClassCastException("Incompatible request conditions: " + clazz + " and " + otherClazz);
		}
	}

	/**
	 * 获取容器中请求条件的匹配结果并用新容器包装；当前容器为空时返回当前实例。
	 */
	@Override
	public RequestConditionHolder getMatchingCondition(HttpServletRequest request) {
		if (this.condition == null) {
			return this;
		}
		RequestCondition<?> match = (RequestCondition<?>) this.condition.getMatchingCondition(request);
		return (match != null ? new RequestConditionHolder(match) : null);
	}

	/**
	 * 确认两个容器中的条件类型后进行比较；其中一个容器为空时，优先选择非空容器。
	 */
	@Override
	public int compareTo(RequestConditionHolder other, HttpServletRequest request) {
		if (this.condition == null && other.condition == null) {
			return 0;
		}
		else if (this.condition == null) {
			return 1;
		}
		else if (other.condition == null) {
			return -1;
		}
		else {
			assertEqualConditionTypes(this.condition, other.condition);
			return this.condition.compareTo(other.condition, request);
		}
	}

}
