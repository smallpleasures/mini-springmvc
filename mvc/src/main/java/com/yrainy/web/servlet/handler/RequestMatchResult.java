
package com.yrainy.web.servlet.handler;

import org.springframework.http.server.PathContainer;
import org.springframework.util.Assert;
import org.springframework.util.PathMatcher;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Map;

/**
 * 保存 {@link MatchableHandlerMapping} 的请求模式匹配结果，并提供从模式中提取 URI 模板变量的方法。
 *
 * @author Rossen Stoyanchev
 * @since 4.3.1
 */
public class RequestMatchResult {
	private final PathPattern pathPattern;
	private final PathContainer lookupPathContainer;
	private final String pattern;
	private final String lookupPath;
	private final PathMatcher pathMatcher;


	/**
	 * 使用匹配到的 {@code PathPattern} 创建结果。
	 * @param pathPattern 匹配到的路径模式
	 * @param lookupPath 用于匹配的请求路径
	 * @since 5.3
	 */
	public RequestMatchResult(PathPattern pathPattern, PathContainer lookupPath) {
		Assert.notNull(pathPattern, "PathPattern is required");
		Assert.notNull(pathPattern, "PathContainer is required");

		this.pattern = null;
		this.lookupPath = null;
		this.pathMatcher = null;

		this.pathPattern = pathPattern;
		this.lookupPathContainer = lookupPath;

	}

	/**
	 * 使用匹配到的字符串模式创建结果。
	 * @param pattern 匹配到的路径模式，可能包含末尾斜杠
	 * @param lookupPath 用于匹配的请求路径
	 * @param pathMatcher 执行匹配的 PathMatcher
	 */
	public RequestMatchResult(String pattern, String lookupPath, PathMatcher pathMatcher) {
		Assert.hasText(pattern, "'matchingPattern' is required");
		Assert.hasText(lookupPath, "'lookupPath' is required");
		Assert.notNull(pathMatcher, "PathMatcher is required");

		this.pattern = pattern;
		this.lookupPath = lookupPath;
		this.pathMatcher = pathMatcher;

		this.pathPattern = null;
		this.lookupPathContainer = null;
	}

	/**
	 * 根据 {@link PathMatcher#extractUriTemplateVariables} 的规则从匹配模式中提取 URI 模板变量。
	 * @return URI 模板变量映射
	 */
	@SuppressWarnings("ConstantConditions")
	public Map<String, String> extractUriTemplateVariables() {
		return (this.pathPattern != null ?
				this.pathPattern.matchAndExtract(this.lookupPathContainer).getUriVariables() :
				this.pathMatcher.extractUriTemplateVariables(this.pattern, this.lookupPath));
	}
}
