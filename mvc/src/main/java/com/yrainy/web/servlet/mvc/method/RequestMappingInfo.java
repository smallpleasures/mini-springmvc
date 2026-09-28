package com.yrainy.web.servlet.mvc.method;

import com.yrainy.web.servlet.mvc.condition.ConsumesRequestCondition;
import com.yrainy.web.servlet.mvc.condition.HeadersRequestCondition;
import com.yrainy.web.servlet.mvc.condition.ParamsRequestCondition;
import com.yrainy.web.servlet.mvc.condition.PatternsRequestCondition;
import com.yrainy.web.servlet.mvc.condition.PathPatternsRequestCondition;
import com.yrainy.web.servlet.mvc.condition.ProducesRequestCondition;
import com.yrainy.web.servlet.mvc.condition.RequestCondition;
import com.yrainy.web.servlet.mvc.condition.RequestConditionHolder;
import com.yrainy.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.http.HttpMethod;
import org.springframework.util.*;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public final class RequestMappingInfo implements RequestCondition<RequestMappingInfo> {

	private static final PathPatternsRequestCondition EMPTY_PATH_PATTERNS = new PathPatternsRequestCondition();

	private static final PatternsRequestCondition EMPTY_PATTERNS = new PatternsRequestCondition();

	private static final RequestMethodsRequestCondition EMPTY_REQUEST_METHODS = new RequestMethodsRequestCondition();

	private static final ParamsRequestCondition EMPTY_PARAMS = new ParamsRequestCondition();

	private static final HeadersRequestCondition EMPTY_HEADERS = new HeadersRequestCondition();

	private static final ConsumesRequestCondition EMPTY_CONSUMES = new ConsumesRequestCondition();

	private static final ProducesRequestCondition EMPTY_PRODUCES = new ProducesRequestCondition();

	private static final RequestConditionHolder EMPTY_CUSTOM = new RequestConditionHolder(null);
	private final String name;
	private final PathPatternsRequestCondition pathPatternsCondition;
	private final PatternsRequestCondition patternsCondition;

	private final RequestMethodsRequestCondition methodsCondition;

	private final ParamsRequestCondition paramsCondition;

	private final HeadersRequestCondition headersCondition;

	private final ConsumesRequestCondition consumesCondition;

	private final ProducesRequestCondition producesCondition;

	private final RequestConditionHolder customConditionHolder;

	private final int hashCode;

	private final BuilderConfiguration options;


	private RequestMappingInfo(String name,
			PathPatternsRequestCondition pathPatternsCondition,
			PatternsRequestCondition patternsCondition,
			RequestMethodsRequestCondition methodsCondition, ParamsRequestCondition paramsCondition,
			HeadersRequestCondition headersCondition, ConsumesRequestCondition consumesCondition,
			ProducesRequestCondition producesCondition, RequestConditionHolder customCondition,
			BuilderConfiguration options) {

		Assert.isTrue(pathPatternsCondition != null || patternsCondition != null,
				"Neither PathPatterns nor String patterns condition");

		this.name = (StringUtils.hasText(name) ? name : null);
		this.pathPatternsCondition = pathPatternsCondition;
		this.patternsCondition = patternsCondition;
		this.methodsCondition = methodsCondition;
		this.paramsCondition = paramsCondition;
		this.headersCondition = headersCondition;
		this.consumesCondition = consumesCondition;
		this.producesCondition = producesCondition;
		this.customConditionHolder = customCondition;
		this.options = options;

		this.hashCode = calculateHashCode(
				this.pathPatternsCondition, this.patternsCondition,
				this.methodsCondition, this.paramsCondition, this.headersCondition,
				this.consumesCondition, this.producesCondition, this.customConditionHolder);
	}


	/** 返回映射名称；未命名时返回 {@code null}。 */
	public String getName() {
		return this.name;
	}

	/** 返回解析后的路径模式条件；与字符串路径条件互斥。 */
	public PathPatternsRequestCondition getPathPatternsCondition() {
		return this.pathPatternsCondition;
	}

	/** 返回基于字符串路径匹配器的路径条件；与解析后的路径条件互斥。 */
	public PatternsRequestCondition getPatternsCondition() {
		return this.patternsCondition;
	}

	/** 返回当前启用的路径模式条件。 */
	@SuppressWarnings("unchecked")
	public <T> RequestCondition<T> getActivePatternsCondition() {
		if (this.pathPatternsCondition != null) {
			return (RequestCondition<T>) this.pathPatternsCondition;
		}
		else if (this.patternsCondition != null) {
			return (RequestCondition<T>) this.patternsCondition;
		}
		else {
			// 构造方法已确保至少存在一种路径条件。
			throw new IllegalStateException();
		}
	}

	/** 返回不包含通配符的映射路径。 */
	public Set<String> getDirectPaths() {
		RequestCondition<?> condition = getActivePatternsCondition();
		return (condition instanceof PathPatternsRequestCondition ?
				((PathPatternsRequestCondition) condition).getDirectPaths() :
				((PatternsRequestCondition) condition).getDirectPaths());
	}

	/** 以字符串集合形式返回当前启用的映射路径。 */
	public Set<String> getPatternValues() {
		RequestCondition<?> condition = getActivePatternsCondition();
		return (condition instanceof PathPatternsRequestCondition ?
				((PathPatternsRequestCondition) condition).getPatternValues() :
				((PatternsRequestCondition) condition).getPatterns());
	}

	/** 返回 HTTP 请求方法条件。 */
	public RequestMethodsRequestCondition getMethodsCondition() {
		return this.methodsCondition;
	}

	/** 返回请求参数条件。 */
	public ParamsRequestCondition getParamsCondition() {
		return this.paramsCondition;
	}

	/** 返回请求头条件。 */
	public HeadersRequestCondition getHeadersCondition() {
		return this.headersCondition;
	}

	/** 返回请求正文媒体类型条件。 */
	public ConsumesRequestCondition getConsumesCondition() {
		return this.consumesCondition;
	}

	/** 返回响应媒体类型条件。 */
	public ProducesRequestCondition getProducesCondition() {
		return this.producesCondition;
	}

	/** 返回自定义请求条件；未配置时返回 {@code null}。 */
	public RequestCondition<?> getCustomCondition() {
		return this.customConditionHolder.getCondition();
	}

	/** 基于当前映射创建新实例，并添加指定的自定义条件。 */
	public RequestMappingInfo addCustomCondition(RequestCondition<?> customCondition) {
		return new RequestMappingInfo(this.name,
				this.pathPatternsCondition, this.patternsCondition,
				this.methodsCondition, this.paramsCondition, this.headersCondition,
				this.consumesCondition, this.producesCondition,
				new RequestConditionHolder(customCondition), this.options);
	}

	/** 合并两组映射条件，例如合并控制器类级和方法级映射。 */
	@Override
	public RequestMappingInfo combine(RequestMappingInfo other) {
		String name = combineNames(other);

		PathPatternsRequestCondition pathPatterns =
				(this.pathPatternsCondition != null && other.pathPatternsCondition != null ?
						this.pathPatternsCondition.combine(other.pathPatternsCondition) : null);

		PatternsRequestCondition patterns =
				(this.patternsCondition != null && other.patternsCondition != null ?
						this.patternsCondition.combine(other.patternsCondition) : null);

		RequestMethodsRequestCondition methods = this.methodsCondition.combine(other.methodsCondition);
		ParamsRequestCondition params = this.paramsCondition.combine(other.paramsCondition);
		HeadersRequestCondition headers = this.headersCondition.combine(other.headersCondition);
		ConsumesRequestCondition consumes = this.consumesCondition.combine(other.consumesCondition);
		ProducesRequestCondition produces = this.producesCondition.combine(other.producesCondition);
		RequestConditionHolder custom = this.customConditionHolder.combine(other.customConditionHolder);

		return new RequestMappingInfo(name, pathPatterns, patterns,
				methods, params, headers, consumes, produces, custom, this.options);
	}
	private String combineNames(RequestMappingInfo other) {
		if (this.name != null && other.name != null) {
			String separator = RequestMappingInfoHandlerMethodMappingNamingStrategy.SEPARATOR;
			return this.name + separator + other.name;
		}
		else if (this.name != null) {
			return this.name;
		}
		else {
			return other.name;
		}
	}

	/** 检查全部映射条件是否匹配当前请求；匹配时返回适用于该请求的映射信息。 */
	@Override
	public RequestMappingInfo getMatchingCondition(HttpServletRequest request) {
		// 检查 HTTP 请求方法条件。
		RequestMethodsRequestCondition methods = this.methodsCondition.getMatchingCondition(request);
		if (methods == null) {
			return null;
		}
		// 检查请求参数条件。
		ParamsRequestCondition params = this.paramsCondition.getMatchingCondition(request);
		if (params == null) {
			return null;
		}
		// 检查请求头条件。
		HeadersRequestCondition headers = this.headersCondition.getMatchingCondition(request);
		if (headers == null) {
			return null;
		}
		// 检查请求正文的媒体类型条件。
		ConsumesRequestCondition consumes = this.consumesCondition.getMatchingCondition(request);
		if (consumes == null) {
			return null;
		}
		// 检查响应媒体类型条件。
		ProducesRequestCondition produces = this.producesCondition.getMatchingCondition(request);
		if (produces == null) {
			return null;
		}
		PathPatternsRequestCondition pathPatterns = null;
		if (this.pathPatternsCondition != null) {
			pathPatterns = this.pathPatternsCondition.getMatchingCondition(request);
			if (pathPatterns == null) {
				return null;
			}
		}
		// 多个路径模式中只保留与当前请求匹配的部分。
		PatternsRequestCondition patterns = null;
		if (this.patternsCondition != null) {
			patterns = this.patternsCondition.getMatchingCondition(request);
			if (patterns == null) {
				return null;
			}
		}
		RequestConditionHolder custom = this.customConditionHolder.getMatchingCondition(request);
		if (custom == null) {
			return null;
		}
		// 返回包含当前请求匹配条件的映射信息。
		return new RequestMappingInfo(this.name, pathPatterns, patterns,
				methods, params, headers, consumes, produces, custom, this.options);
	}

	/** 按当前请求比较两组已匹配的映射条件，优先返回更具体的映射。 */
	@Override
	public int compareTo(RequestMappingInfo other, HttpServletRequest request) {
		int result;
		// 优先比较 HEAD 请求的显式或隐式映射。
		if (HttpMethod.HEAD.matches(request.getMethod())) {
			result = this.methodsCondition.compareTo(other.getMethodsCondition(), request);
			if (result != 0) {
				return result;
			}
		}
		result = getActivePatternsCondition().compareTo(other.getActivePatternsCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.paramsCondition.compareTo(other.getParamsCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.headersCondition.compareTo(other.getHeadersCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.consumesCondition.compareTo(other.getConsumesCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.producesCondition.compareTo(other.getProducesCondition(), request);
		if (result != 0) {
			return result;
		}
		// 未限定请求方法的映射优先级低于显式方法映射。
		result = this.methodsCondition.compareTo(other.getMethodsCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.customConditionHolder.compareTo(other.customConditionHolder, request);
		if (result != 0) {
			return result;
		}
		return 0;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof RequestMappingInfo)) {
			return false;
		}
		RequestMappingInfo otherInfo = (RequestMappingInfo) other;
		return (getActivePatternsCondition().equals(otherInfo.getActivePatternsCondition()) &&
				this.methodsCondition.equals(otherInfo.methodsCondition) &&
				this.paramsCondition.equals(otherInfo.paramsCondition) &&
				this.headersCondition.equals(otherInfo.headersCondition) &&
				this.consumesCondition.equals(otherInfo.consumesCondition) &&
				this.producesCondition.equals(otherInfo.producesCondition) &&
				this.customConditionHolder.equals(otherInfo.customConditionHolder));
	}

	@Override
	public int hashCode() {
		return this.hashCode;
	}

	@SuppressWarnings("ConstantConditions")
	private static int calculateHashCode(
			PathPatternsRequestCondition pathPatterns, PatternsRequestCondition patterns,
			RequestMethodsRequestCondition methods, ParamsRequestCondition params, HeadersRequestCondition headers,
			ConsumesRequestCondition consumes, ProducesRequestCondition produces, RequestConditionHolder custom) {

		return (pathPatterns != null ? pathPatterns : patterns).hashCode() * 31 +
				methods.hashCode() + params.hashCode() +
				headers.hashCode() + consumes.hashCode() + produces.hashCode() +
				custom.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("{");
		if (!this.methodsCondition.isEmpty()) {
			Set<RequestMethod> httpMethods = this.methodsCondition.getMethods();
			builder.append(httpMethods.size() == 1 ? httpMethods.iterator().next() : httpMethods);
		}

		// 路径条件至少包含一个空路径项。
		builder.append(' ').append(getActivePatternsCondition());

		if (!this.paramsCondition.isEmpty()) {
			builder.append(", params ").append(this.paramsCondition);
		}
		if (!this.headersCondition.isEmpty()) {
			builder.append(", headers ").append(this.headersCondition);
		}
		if (!this.consumesCondition.isEmpty()) {
			builder.append(", consumes ").append(this.consumesCondition);
		}
		if (!this.producesCondition.isEmpty()) {
			builder.append(", produces ").append(this.producesCondition);
		}
		if (!this.customConditionHolder.isEmpty()) {
			builder.append(", and ").append(this.customConditionHolder);
		}
		builder.append('}');
		return builder.toString();
	}

	/** 创建一个基于当前映射修改条件的构造器。 */
	public Builder mutate() {
		return new MutateBuilder(this);
	}


	/** 创建使用指定路径的映射构造器。 */
	public static Builder paths(String... paths) {
		return new DefaultBuilder(paths);
	}


	/** 用于构造 RequestMappingInfo 的配置接口。 */
	public interface Builder {

		/** 设置 URL 路径模式。 */
		Builder paths(String... paths);

		/** 设置 HTTP 请求方法条件。 */
		Builder methods(RequestMethod... methods);

		/** 设置请求参数条件。 */
		Builder params(String... params);

		/** 设置请求头条件。 */
		Builder headers(String... headers);

		/** 设置请求正文媒体类型条件。 */
		Builder consumes(String... consumes);

		/** 设置响应媒体类型条件。 */
		Builder produces(String... produces);

		/** 设置映射名称。 */
		Builder mappingName(String name);

		/** 设置自定义请求条件。 */
		Builder customCondition(RequestCondition<?> condition);

		/** 设置创建映射时使用的配置参数。 */
		Builder options(BuilderConfiguration options);

		/** 根据当前条件构造 RequestMappingInfo。 */
		RequestMappingInfo build();
	}


	private static class DefaultBuilder implements Builder {

		private String[] paths;

		private RequestMethod[] methods = new RequestMethod[0];

		private String[] params = new String[0];

		private String[] headers = new String[0];

		private String[] consumes = new String[0];

		private String[] produces = new String[0];

		private boolean hasContentType;

		private boolean hasAccept;
		private String mappingName;
		private RequestCondition<?> customCondition;

		private BuilderConfiguration options = new BuilderConfiguration();

		public DefaultBuilder(String... paths) {
			this.paths = paths;
		}

		@Override
		public Builder paths(String... paths) {
			this.paths = paths;
			return this;
		}

		@Override
		public DefaultBuilder methods(RequestMethod... methods) {
			this.methods = methods;
			return this;
		}

		@Override
		public DefaultBuilder params(String... params) {
			this.params = params;
			return this;
		}

		@Override
		public DefaultBuilder headers(String... headers) {
			for (String header : headers) {
				this.hasContentType = this.hasContentType ||
						header.contains("Content-Type") || header.contains("content-type");
				this.hasAccept = this.hasAccept ||
						header.contains("Accept") || header.contains("accept");
			}
			this.headers = headers;
			return this;
		}

		@Override
		public DefaultBuilder consumes(String... consumes) {
			this.consumes = consumes;
			return this;
		}

		@Override
		public DefaultBuilder produces(String... produces) {
			this.produces = produces;
			return this;
		}

		@Override
		public DefaultBuilder mappingName(String name) {
			this.mappingName = name;
			return this;
		}

		@Override
		public DefaultBuilder customCondition(RequestCondition<?> condition) {
			this.customCondition = condition;
			return this;
		}

		@Override
		public Builder options(BuilderConfiguration options) {
			this.options = options;
			return this;
		}

		@Override
		@SuppressWarnings("deprecation")
		public RequestMappingInfo build() {

			PathPatternsRequestCondition pathPatterns = null;
			PatternsRequestCondition patterns = null;

			if (this.options.patternParser != null) {
				pathPatterns = (ObjectUtils.isEmpty(this.paths) ?
						EMPTY_PATH_PATTERNS :
						new PathPatternsRequestCondition(this.options.patternParser, this.paths));
			}
			else {
				patterns = (ObjectUtils.isEmpty(this.paths) ?
						EMPTY_PATTERNS :
						new PatternsRequestCondition(
								this.paths, null, this.options.getPathMatcher(),
								this.options.useSuffixPatternMatch(), this.options.useTrailingSlashMatch(),
								this.options.getFileExtensions()));
			}

			ContentNegotiationManager manager = this.options.getContentNegotiationManager();

			return new RequestMappingInfo(
					this.mappingName, pathPatterns, patterns,
					ObjectUtils.isEmpty(this.methods) ?
							EMPTY_REQUEST_METHODS : new RequestMethodsRequestCondition(this.methods),
					ObjectUtils.isEmpty(this.params) ?
							EMPTY_PARAMS : new ParamsRequestCondition(this.params),
					ObjectUtils.isEmpty(this.headers) ?
							EMPTY_HEADERS : new HeadersRequestCondition(this.headers),
					ObjectUtils.isEmpty(this.consumes) && !this.hasContentType ?
							EMPTY_CONSUMES : new ConsumesRequestCondition(this.consumes, this.headers),
					ObjectUtils.isEmpty(this.produces) && !this.hasAccept ?
							EMPTY_PRODUCES : new ProducesRequestCondition(this.produces, this.headers, manager),
					this.customCondition != null ?
							new RequestConditionHolder(this.customCondition) : EMPTY_CUSTOM,
					this.options);
		}
	}


	private static class MutateBuilder implements Builder {
		private String name;
		private PathPatternsRequestCondition pathPatternsCondition;
		private PatternsRequestCondition patternsCondition;

		private RequestMethodsRequestCondition methodsCondition;

		private ParamsRequestCondition paramsCondition;

		private HeadersRequestCondition headersCondition;

		private ConsumesRequestCondition consumesCondition;

		private ProducesRequestCondition producesCondition;

		private RequestConditionHolder customConditionHolder;

		private BuilderConfiguration options;

		public MutateBuilder(RequestMappingInfo other) {
			this.name = other.name;
			this.pathPatternsCondition = other.pathPatternsCondition;
			this.patternsCondition = other.patternsCondition;
			this.methodsCondition = other.methodsCondition;
			this.paramsCondition = other.paramsCondition;
			this.headersCondition = other.headersCondition;
			this.consumesCondition = other.consumesCondition;
			this.producesCondition = other.producesCondition;
			this.customConditionHolder = other.customConditionHolder;
			this.options = other.options;
		}

		@Override
		@SuppressWarnings("deprecation")
		public Builder paths(String... paths) {
			if (this.options.patternParser != null) {
				this.pathPatternsCondition = (ObjectUtils.isEmpty(paths) ?
						EMPTY_PATH_PATTERNS : new PathPatternsRequestCondition(this.options.patternParser, paths));
			}
			else {
				this.patternsCondition = (ObjectUtils.isEmpty(paths) ?
						EMPTY_PATTERNS :
						new PatternsRequestCondition(
								paths, null, this.options.getPathMatcher(),
								this.options.useSuffixPatternMatch(), this.options.useTrailingSlashMatch(),
								this.options.getFileExtensions()));
			}
			return this;
		}

		@Override
		public Builder methods(RequestMethod... methods) {
			this.methodsCondition = (ObjectUtils.isEmpty(methods) ?
					EMPTY_REQUEST_METHODS : new RequestMethodsRequestCondition(methods));
			return this;
		}

		@Override
		public Builder params(String... params) {
			this.paramsCondition = (ObjectUtils.isEmpty(params) ?
					EMPTY_PARAMS : new ParamsRequestCondition(params));
			return this;
		}

		@Override
		public Builder headers(String... headers) {
			this.headersCondition = (ObjectUtils.isEmpty(headers) ?
					EMPTY_HEADERS : new HeadersRequestCondition(headers));
			return this;
		}

		@Override
		public Builder consumes(String... consumes) {
			this.consumesCondition = (ObjectUtils.isEmpty(consumes) ?
					EMPTY_CONSUMES : new ConsumesRequestCondition(consumes));
			return this;
		}

		@Override
		public Builder produces(String... produces) {
			this.producesCondition = (ObjectUtils.isEmpty(produces) ?
					EMPTY_PRODUCES :
					new ProducesRequestCondition(produces, null, this.options.getContentNegotiationManager()));
			return this;
		}

		@Override
		public Builder mappingName(String name) {
			this.name = name;
			return this;
		}

		@Override
		public Builder customCondition(RequestCondition<?> condition) {
			this.customConditionHolder = new RequestConditionHolder(condition);
			return this;
		}

		@Override
		public Builder options(BuilderConfiguration options) {
			this.options = options;
			return this;
		}

		@Override
		public RequestMappingInfo build() {
			return new RequestMappingInfo(this.name,
					this.pathPatternsCondition, this.patternsCondition,
					this.methodsCondition, this.paramsCondition, this.headersCondition,
					this.consumesCondition, this.producesCondition,
					this.customConditionHolder, this.options);
		}
	}


	/** 保存创建 RequestMappingInfo 时使用的路径匹配和内容协商配置。 */
	public static class BuilderConfiguration {
		private PathPatternParser patternParser;
		private PathMatcher pathMatcher;

		private boolean trailingSlashMatch = true;

		private boolean suffixPatternMatch = false;

		private boolean registeredSuffixPatternMatch = false;
		private ContentNegotiationManager contentNegotiationManager;


		/** 设置解析后的路径模式解析器；启用后不能同时设置 PathMatcher。 */
		public void setPatternParser(PathPatternParser patternParser) {
			this.patternParser = patternParser;
		}

		/** 返回当前路径模式解析器。 */
		public PathPatternParser getPatternParser() {
			return this.patternParser;
		}

		/** 设置字符串路径条件使用的路径匹配器。 */
		public void setPathMatcher(PathMatcher pathMatcher) {
			this.pathMatcher = pathMatcher;
		}

		/** 返回当前路径匹配器。 */
		public PathMatcher getPathMatcher() {
			return this.pathMatcher;
		}

		/** 设置路径条件是否忽略末尾斜杠。 */
		public void setTrailingSlashMatch(boolean trailingSlashMatch) {
			this.trailingSlashMatch = trailingSlashMatch;
		}

		/** 返回路径条件是否忽略末尾斜杠。 */
		public boolean useTrailingSlashMatch() {
			return this.trailingSlashMatch;
		}

		/** 设置是否启用路径后缀匹配。 */
		public void setSuffixPatternMatch(boolean suffixPatternMatch) {
			this.suffixPatternMatch = suffixPatternMatch;
		}

		/** 返回路径后缀匹配开关。 */
		@Deprecated
		public boolean useSuffixPatternMatch() {
			return this.suffixPatternMatch;
		}

		/** 设置仅允许已注册的文件后缀参与路径匹配。 */
		public void setRegisteredSuffixPatternMatch(boolean registeredSuffixPatternMatch) {
			this.registeredSuffixPatternMatch = registeredSuffixPatternMatch;
		}


		/** 返回是否仅允许已注册扩展名参与后缀匹配。 */
		@Deprecated
		public boolean useRegisteredSuffixPatternMatch() {
			return this.registeredSuffixPatternMatch;
		}

		/** 返回用于后缀匹配的扩展名；开启注册扩展名模式时从内容协商管理器获取。 */
		@Deprecated
		public List<String> getFileExtensions() {
			if (useRegisteredSuffixPatternMatch() && this.contentNegotiationManager != null) {
				return this.contentNegotiationManager.getAllFileExtensions();
			}
			return null;
		}

		/** 设置响应媒体类型条件使用的内容协商管理器。 */
		public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
			this.contentNegotiationManager = contentNegotiationManager;
		}

		/** 返回当前内容协商管理器。 */
		public ContentNegotiationManager getContentNegotiationManager() {
			return this.contentNegotiationManager;
		}
	}

}
