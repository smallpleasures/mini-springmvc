package com.yrainy.web.servlet.mvc.condition;

import javax.servlet.http.HttpServletRequest;

public interface RequestCondition<T> {

	/**
	 * 将当前条件与另一个条件合并，例如合并类型级和方法级 {@code @RequestMapping} 条件。
	 * @param other 要合并的条件
	 * @return 合并后的请求条件
	 */
	T combine(T other);

	/**
	 * 检查条件是否匹配当前请求，并返回可能针对当前请求生成的新实例。例如，多个 URL 模式中只保留匹配项。
	 * <p>处理 CORS 预检请求时，应按预期的实际请求进行匹配，例如检查路径、查询参数和
	 * {@code Access-Control-Request-Method} 请求头。若条件无法用于预检请求，应返回内容为空的条件，避免匹配失败。
	 * @return 匹配时返回对应条件，否则返回 {@code null}
	 */
	T getMatchingCondition(HttpServletRequest request);

	/**
	 * 在给定请求的上下文中比较当前条件和另一个条件。假定两个条件均由
	 * {@link #getMatchingCondition(HttpServletRequest)} 生成，且只包含与当前请求相关的内容。
	 */
	int compareTo(T other, HttpServletRequest request);

}
