
package com.yrainy.web.servlet.handler;

import org.springframework.http.server.PathContainer;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.request.WebRequestInterceptor;
import com.yrainy.web.servlet.HandlerInterceptor;
import com.yrainy.web.servlet.HandlerMapping;
import com.yrainy.web.servlet.ModelAndView;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * 包装 {@link HandlerInterceptor}，并根据 URL 路径模式判断是否将其应用于当前请求。
 *
 * <p>路径匹配可使用 {@link PathMatcher} 或解析后的 {@link PathPattern}。两者语法大体相同，
 * PathPattern 更适合 Web 场景且效率更高。具体使用哪种方式取决于当前请求是否已由
 * {@link UrlPathHelper#resolveAndCacheLookupPath} 解析为字符串路径，或由
 * {@link ServletRequestPathUtils#parseAndCache} 解析为 RequestPath；这由匹配请求的
 * {@link HandlerMapping} 决定。
 *
 * <p>{@code MappedInterceptor} 可由
 * {@link org.springframework.web.servlet.handler.AbstractHandlerMethodMapping}
 * 的子类识别。这些子类会查找此类型的 Bean，并检查直接注册的拦截器是否为此类型。
 *
 * @author Keith Donald
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.0
 */
public final class MappedInterceptor implements HandlerInterceptor {

	private static PathMatcher defaultPathMatcher = new AntPathMatcher();
	private final PatternAdapter[] includePatterns;
	private final PatternAdapter[] excludePatterns;

	private PathMatcher pathMatcher = defaultPathMatcher;

	private final HandlerInterceptor interceptor;


	/**
	 * 使用包含模式、排除模式和目标拦截器创建映射拦截器。
	 * @param includePatterns 请求必须匹配的路径模式；为 {@code null} 时匹配所有路径
	 * @param excludePatterns 请求不得匹配的路径模式
	 * @param interceptor 目标拦截器
	 * @param parser 用于预解析 {@link PathPattern} 的解析器；未提供时使用 {@link PathPatternParser#defaultInstance}
	 * @since 5.3
	 */
	public MappedInterceptor(String[] includePatterns, String[] excludePatterns,
                             HandlerInterceptor interceptor, PathPatternParser parser) {

		this.includePatterns = PatternAdapter.initPatterns(includePatterns, parser);
		this.excludePatterns = PatternAdapter.initPatterns(excludePatterns, parser);
		this.interceptor = interceptor;
	}


	/**
	 * 仅指定包含模式的构造方法。
	 */
	public MappedInterceptor(String[] includePatterns, HandlerInterceptor interceptor) {
		this(includePatterns, null, interceptor);
	}

	/**
	 * 不指定解析器的构造方法。
	 */
	public MappedInterceptor(String[] includePatterns, String[] excludePatterns,
                             HandlerInterceptor interceptor) {

		this(includePatterns, excludePatterns, interceptor, null);
	}

	/**
	 * 使用 {@link WebRequestInterceptor} 作为目标拦截器的构造方法。
	 */
	public MappedInterceptor(String[] includePatterns, WebRequestInterceptor interceptor) {
		this(includePatterns, null, interceptor);
	}

	/**
	 * 使用 {@link WebRequestInterceptor} 并同时指定包含和排除模式的构造方法。
	 */
	public MappedInterceptor(String[] includePatterns, String[] excludePatterns,
                             WebRequestInterceptor interceptor) {

		this(includePatterns, excludePatterns, new WebRequestHandlerInterceptorAdapter(interceptor));
	}


	/**
	 * 返回映射到此拦截器的包含路径模式。
	 */
	public String[] getPathPatterns() {
		return (!ObjectUtils.isEmpty(this.includePatterns) ?
				Arrays.stream(this.includePatterns).map(PatternAdapter::getPatternString).toArray(String[]::new) :
				null);
	}

	/**
	 * 返回匹配时要调用的目标 {@link HandlerInterceptor}。
	 */
	public HandlerInterceptor getInterceptor() {
		return this.interceptor;
	}

	/**
	 * 设置用于将 URL 路径与包含、排除模式进行匹配的 PathMatcher。
	 * <p>仅在需要自定义 {@link AntPathMatcher} 或其他 PathMatcher 时使用此高级配置；默认使用 {@link AntPathMatcher}。
	 * <p><strong>注意：</strong>设置 PathMatcher 后，即使已有解析后的 RequestPath，也会强制使用字符串模式匹配。
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	/**
	 * 返回通过 {@link #setPathMatcher(PathMatcher)} 配置的 PathMatcher。
	 */
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}


	/**
	 * 检查此拦截器是否映射到当前请求。调用前应已解析请求路径，详见类级说明。
	 * @param request 要匹配的请求
	 * @return 此拦截器适用于当前请求时返回 {@code true}
	 */
	public boolean matches(HttpServletRequest request) {
		Object path = ServletRequestPathUtils.getCachedPath(request);
		if (this.pathMatcher != defaultPathMatcher) {
			path = path.toString();
		}
		boolean isPathContainer = (path instanceof PathContainer);
		if (!ObjectUtils.isEmpty(this.excludePatterns)) {
			for (PatternAdapter adapter : this.excludePatterns) {
				if (adapter.match(path, isPathContainer, this.pathMatcher)) {
					return false;
				}
			}
		}
		if (ObjectUtils.isEmpty(this.includePatterns)) {
			return true;
		}
		for (PatternAdapter adapter : this.includePatterns) {
			if (adapter.match(path, isPathContainer, this.pathMatcher)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 检查给定查找路径是否匹配此拦截器。
	 * @param lookupPath 当前请求路径
	 * @param pathMatcher 用于匹配路径模式的 PathMatcher
	 * @return 此拦截器适用于给定请求路径时返回 {@code true}
	 * @deprecated as of 5.3 in favor of {@link #matches(HttpServletRequest)}
	 */
	@Deprecated
	public boolean matches(String lookupPath, PathMatcher pathMatcher) {
		pathMatcher = (this.pathMatcher != defaultPathMatcher ? this.pathMatcher : pathMatcher);
		if (!ObjectUtils.isEmpty(this.excludePatterns)) {
			for (PatternAdapter adapter : this.excludePatterns) {
				if (pathMatcher.match(adapter.getPatternString(), lookupPath)) {
					return false;
				}
			}
		}
		if (ObjectUtils.isEmpty(this.includePatterns)) {
			return true;
		}
		for (PatternAdapter adapter : this.includePatterns) {
			if (pathMatcher.match(adapter.getPatternString(), lookupPath)) {
				return true;
			}
		}
		return false;
	}


	// 将拦截器回调委托给目标实例。

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		return this.interceptor.preHandle(request, response, handler);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

		this.interceptor.postHandle(request, response, handler, modelAndView);
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) throws Exception {

		this.interceptor.afterCompletion(request, response, handler, ex);
	}


	/**
	 * 同时保存解析后的 {@link PathPattern} 和原始字符串模式。缓存路径为 {@link PathContainer} 时优先使用解析模式，
	 * 否则使用字符串匹配；模式语法不受支持而无法解析时，所有请求均使用 {@link PathMatcher} 匹配。
	 * @since 5.3.6
	 */
	private static class PatternAdapter {

		private final String patternString;
		private final PathPattern pathPattern;


		public PatternAdapter(String pattern, PathPatternParser parser) {
			this.patternString = pattern;
			this.pathPattern = initPathPattern(pattern, parser);
		}
		private static PathPattern initPathPattern(String pattern, PathPatternParser parser) {
			try {
				return (parser != null ? parser : PathPatternParser.defaultInstance).parse(pattern);
			}
			catch (PatternParseException ex) {
				return null;
			}
		}

		public String getPatternString() {
			return this.patternString;
		}

		public boolean match(Object path, boolean isPathContainer, PathMatcher pathMatcher) {
			if (isPathContainer) {
				PathContainer pathContainer = (PathContainer) path;
				if (this.pathPattern != null) {
					return this.pathPattern.matches(pathContainer);
				}
				String lookupPath = pathContainer.value();
				path = UrlPathHelper.defaultInstance.removeSemicolonContent(lookupPath);
			}
			return pathMatcher.match(this.patternString, (String) path);
		}
		public static PatternAdapter[] initPatterns(
				String[] patterns, PathPatternParser parser) {

			if (ObjectUtils.isEmpty(patterns)) {
				return null;
			}
			return Arrays.stream(patterns)
					.map(pattern -> new PatternAdapter(pattern, parser))
					.toArray(PatternAdapter[]::new);
		}
	}

}
