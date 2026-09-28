
package com.yrainy.web.servlet.mvc.condition;

import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.cors.CorsUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 用于将请求的 {@code Content-Type} 与媒体类型表达式列表进行逻辑或匹配的请求条件。
 * <p>表达式可来自 {@link RequestMapping#consumes()}，也可来自
 * {@link RequestMapping#headers()} 中名称为 {@code Content-Type} 的条件；两种写法语义相同。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ConsumesRequestCondition extends AbstractRequestCondition<ConsumesRequestCondition> {

	private static final ConsumesRequestCondition EMPTY_CONDITION = new ConsumesRequestCondition();


	private final List<ConsumeMediaTypeExpression> expressions;

	private boolean bodyRequired = true;


	/**
	 * 根据零个或多个 consumes 表达式创建请求条件。
	 * @param consumes 表达式语法参见 {@link RequestMapping#consumes()}；为空时匹配所有请求
	 */
	public ConsumesRequestCondition(String... consumes) {
		this(consumes, null);
	}

	/**
	 * 根据 consumes 和请求头表达式创建请求条件。
	 * <p>请求头名称不是 {@code Content-Type} 或未指定值的表达式会被忽略；两组表达式均为空时匹配所有请求。
	 * @param consumes 语法参见 {@link RequestMapping#consumes()}
	 * @param headers 语法参见 {@link RequestMapping#headers()}
	 */
	public ConsumesRequestCondition(String[] consumes, String[] headers) {
		this.expressions = parseExpressions(consumes, headers);
		if (this.expressions.size() > 1) {
			Collections.sort(this.expressions);
		}
	}

	private static List<ConsumeMediaTypeExpression> parseExpressions(String[] consumes, String[] headers) {
		Set<ConsumeMediaTypeExpression> result = null;
		if (!ObjectUtils.isEmpty(headers)) {
			for (String header : headers) {
				String expression = header.trim();
				boolean negated = expression.startsWith("!");
				if (negated) {
					expression = expression.substring(1).trim();
				}
				int separator = expression.indexOf("!=");
				if (separator >= 0) {
					negated = true;
				}
				else {
					separator = expression.indexOf('=');
				}
				if (separator >= 0) {
					String name = expression.substring(0, separator).trim();
					int valueStart = expression.charAt(separator) == '!' ? separator + 2 : separator + 1;
					String value = expression.substring(valueStart).trim();
					if ("Content-Type".equalsIgnoreCase(name) && StringUtils.hasText(value)) {
					result = (result != null ? result : new LinkedHashSet<>());
					for (MediaType mediaType : MediaType.parseMediaTypes(value)) {
						result.add(new ConsumeMediaTypeExpression(mediaType, negated));
					}
				}
			}
			}
		}
		if (!ObjectUtils.isEmpty(consumes)) {
			result = (result != null ? result : new LinkedHashSet<>());
			for (String consume : consumes) {
				result.add(new ConsumeMediaTypeExpression(consume));
			}
		}
		return (result != null ? new ArrayList<>(result) : Collections.emptyList());
	}

	/**
	 * 创建匹配结果时使用的私有构造方法；表达式列表不会排序或深拷贝。
	 */
	private ConsumesRequestCondition(List<ConsumeMediaTypeExpression> expressions) {
		this.expressions = expressions;
	}


	/**
	 * 返回包含的媒体类型表达式。
	 */
	public Set<MediaTypeExpression> getExpressions() {
		return new LinkedHashSet<>(this.expressions);
	}

	/**
	 * 返回此条件中未取反的媒体类型。
	 */
	public Set<MediaType> getConsumableMediaTypes() {
		Set<MediaType> result = new LinkedHashSet<>();
		for (ConsumeMediaTypeExpression expression : this.expressions) {
			if (!expression.isNegated()) {
				result.add(expression.getMediaType());
			}
		}
		return result;
	}

	/**
	 * 此条件是否包含媒体类型表达式。
	 */
	@Override
	public boolean isEmpty() {
		return this.expressions.isEmpty();
	}

	@Override
	protected Collection<ConsumeMediaTypeExpression> getContent() {
		return this.expressions;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	/**
	 * 设置是否要求请求包含请求体。
	 * <p>默认值为 {@code true}，此时根据 {@code Content-Type} 请求头匹配；未提供时按
	 * {@code application/octet-stream} 处理。
	 * <p>设为 {@code false} 且请求没有请求体时，无需检查表达式即可匹配。
	 * @param bodyRequired 是否要求请求包含请求体
	 * @since 5.2
	 */
	public void setBodyRequired(boolean bodyRequired) {
		this.bodyRequired = bodyRequired;
	}

	/**
	 * 返回 {@link #setBodyRequired(boolean)} 设置的值。
	 * @since 5.2
	 */
	public boolean isBodyRequired() {
		return this.bodyRequired;
	}


	/**
	 * 合并两个条件。若方法级条件包含表达式，则返回该条件，否则沿用类型级条件。
	 */
	@Override
	public ConsumesRequestCondition combine(ConsumesRequestCondition other) {
		return (!other.expressions.isEmpty() ? other : this);
	}

	/**
	 * 检查媒体类型表达式是否匹配请求的 {@code Content-Type}，并返回仅包含匹配表达式的条件。
	 * <p>匹配规则由 {@link MediaType#includes(MediaType)} 提供。
	 * @param request 当前请求
	 * @return 条件为空时返回当前实例；匹配时返回仅含匹配项的新实例；均不匹配时返回 {@code null}
	 */
	@Override
	public ConsumesRequestCondition getMatchingCondition(HttpServletRequest request) {
		if (CorsUtils.isPreFlightRequest(request)) {
			return EMPTY_CONDITION;
		}
		if (isEmpty()) {
			return this;
		}
		if (!hasBody(request) && !this.bodyRequired) {
			return EMPTY_CONDITION;
		}

		// 常见媒体类型由 MimeTypeUtils 统一缓存。

		MediaType contentType;
		try {
			contentType = StringUtils.hasLength(request.getContentType()) ?
					MediaType.parseMediaType(request.getContentType()) :
					MediaType.APPLICATION_OCTET_STREAM;
		}
		catch (InvalidMediaTypeException ex) {
			return null;
		}

		List<ConsumeMediaTypeExpression> result = getMatchingExpressions(contentType);
		return !CollectionUtils.isEmpty(result) ? new ConsumesRequestCondition(result) : null;
	}

	private boolean hasBody(HttpServletRequest request) {
		String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
		String transferEncoding = request.getHeader(HttpHeaders.TRANSFER_ENCODING);
		return StringUtils.hasText(transferEncoding) ||
				(StringUtils.hasText(contentLength) && !contentLength.trim().equals("0"));
	}
	private List<ConsumeMediaTypeExpression> getMatchingExpressions(MediaType contentType) {
		List<ConsumeMediaTypeExpression> result = null;
		for (ConsumeMediaTypeExpression expression : this.expressions) {
			if (expression.match(contentType)) {
				result = result != null ? result : new ArrayList<>();
				result.add(expression);
			}
		}
		return result;
	}

	/**
	 * 比较两个条件的匹配优先级：
	 * <ul>
	 * <li>两个条件的表达式数量相同时返回 0</li>
	 * <li>当前条件表达式更多或媒体类型更具体时返回负数</li>
	 * <li>另一个条件表达式更多或媒体类型更具体时返回正数</li>
	 * </ul>
	 * <p>假定两个条件均由 {@link #getMatchingCondition(HttpServletRequest)} 生成，且各自只包含匹配的
	 * 可消费媒体类型表达式，或为空。
	 */
	@Override
	public int compareTo(ConsumesRequestCondition other, HttpServletRequest request) {
		if (this.expressions.isEmpty() && other.expressions.isEmpty()) {
			return 0;
		}
		else if (this.expressions.isEmpty()) {
			return 1;
		}
		else if (other.expressions.isEmpty()) {
			return -1;
		}
		else {
			return this.expressions.get(0).compareTo(other.expressions.get(0));
		}
	}


	/**
	 * 解析单个媒体类型表达式，并检查其是否匹配请求的 {@code Content-Type}。
	 */
	static class ConsumeMediaTypeExpression extends AbstractMediaTypeExpression {

		ConsumeMediaTypeExpression(String expression) {
			super(expression);
		}

		ConsumeMediaTypeExpression(MediaType mediaType, boolean negated) {
			super(mediaType, negated);
		}

		public final boolean match(MediaType contentType) {
			boolean match = getMediaType().includes(contentType);
			return !isNegated() == match;
		}
	}

}
