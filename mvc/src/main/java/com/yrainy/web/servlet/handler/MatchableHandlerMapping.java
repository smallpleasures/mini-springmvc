
package com.yrainy.web.servlet.handler;

import com.yrainy.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.servlet.http.HttpServletRequest;

/**
 * {@link HandlerMapping} 可实现的扩展接口，用于公开与其内部配置和实现一致的请求匹配能力。
 *
 * @author Rossen Stoyanchev
 * @since 4.3.1
 * @see HandlerMappingIntrospector
 */
public interface MatchableHandlerMapping extends HandlerMapping {

	/**
	 * 返回此 {@code HandlerMapping} 配置的路径解析器；已配置时使用预解析的路径模式。
	 * @since 5.3
	 */
	default PathPatternParser getPatternParser() {
		return null;
	}

	/**
	 * 检查请求是否匹配给定模式。{@link #getPatternParser()} 返回 {@code null} 时，
	 * {@code HandlerMapping} 使用字符串模式匹配。
	 * @param request 当前请求
	 * @param pattern 要匹配的路径模式
	 * @return 匹配结果；未匹配时返回 {@code null}
	 */
	RequestMatchResult match(HttpServletRequest request, String pattern);

}
