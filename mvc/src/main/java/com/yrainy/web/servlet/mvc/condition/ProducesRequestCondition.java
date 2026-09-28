package com.yrainy.web.servlet.mvc.condition;

import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.ServletWebRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/** 匹配客户端 Accept 请求头与控制器声明的响应媒体类型。 */
public final class ProducesRequestCondition extends AbstractRequestCondition<ProducesRequestCondition> {

	private final List<ProduceMediaTypeExpression> expressions;
	private final ContentNegotiationManager contentNegotiationManager;

	public ProducesRequestCondition(String... produces) {
		this(produces, null, new ContentNegotiationManager());
	}

	public ProducesRequestCondition(String[] produces, String[] headers,
			ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = (contentNegotiationManager != null ?
				contentNegotiationManager : new ContentNegotiationManager());
		Set<ProduceMediaTypeExpression> parsed = new LinkedHashSet<>();
		if (headers != null) {
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
				if (separator >= 0 && "Accept".equalsIgnoreCase(expression.substring(0, separator).trim())) {
					int valueStart = expression.charAt(separator) == '!' ? separator + 2 : separator + 1;
					for (MediaType mediaType : MediaType.parseMediaTypes(expression.substring(valueStart).trim())) {
						parsed.add(new ProduceMediaTypeExpression(mediaType, negated));
					}
				}
			}
		}
		for (String expression : produces) {
			parsed.add(new ProduceMediaTypeExpression(expression));
		}
		this.expressions = new ArrayList<>(parsed);
		Collections.sort(this.expressions);
	}

	private ProducesRequestCondition(List<ProduceMediaTypeExpression> expressions,
			ContentNegotiationManager contentNegotiationManager) {
		this.expressions = expressions;
		this.contentNegotiationManager = contentNegotiationManager;
	}

	public Set<MediaType> getProducibleMediaTypes() {
		Set<MediaType> result = new LinkedHashSet<>();
		for (ProduceMediaTypeExpression expression : this.expressions) {
			if (!expression.isNegated()) {
				result.add(expression.getMediaType());
			}
		}
		return result;
	}

	@Override
	protected Collection<ProduceMediaTypeExpression> getContent() {
		return this.expressions;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	@Override
	public ProducesRequestCondition combine(ProducesRequestCondition other) {
		return (!other.expressions.isEmpty() ? other : this);
	}

	@Override
	public ProducesRequestCondition getMatchingCondition(HttpServletRequest request) {
		if (this.expressions.isEmpty()) {
			return this;
		}
		List<MediaType> accepted;
		try {
			accepted = this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
		}
		catch (HttpMediaTypeNotAcceptableException ex) {
			return null;
		}
		List<ProduceMediaTypeExpression> matches = new ArrayList<>();
		for (ProduceMediaTypeExpression expression : this.expressions) {
			if (expression.match(accepted)) {
				matches.add(expression);
			}
		}
		return (matches.isEmpty() ? null :
				new ProducesRequestCondition(matches, this.contentNegotiationManager));
	}

	@Override
	public int compareTo(ProducesRequestCondition other, HttpServletRequest request) {
		if (this.expressions.isEmpty() && other.expressions.isEmpty()) {
			return 0;
		}
		if (this.expressions.isEmpty()) {
			return 1;
		}
		if (other.expressions.isEmpty()) {
			return -1;
		}
		return this.expressions.get(0).compareTo(other.expressions.get(0));
	}

	/** 清除请求中的媒体类型缓存；当前实现不向请求写入临时缓存。 */
	public static void clearMediaTypesAttribute(HttpServletRequest request) {
		request.removeAttribute(ProducesRequestCondition.class.getName() + ".MEDIA_TYPES");
	}

	private static final class ProduceMediaTypeExpression extends AbstractMediaTypeExpression {
		private ProduceMediaTypeExpression(String expression) {
			super(expression);
		}

		private ProduceMediaTypeExpression(MediaType mediaType, boolean negated) {
			super(mediaType, negated);
		}

		private boolean match(List<MediaType> accepted) {
			boolean compatible = false;
			for (MediaType acceptedType : accepted) {
				if (getMediaType().isCompatibleWith(acceptedType)) {
					compatible = true;
					break;
				}
			}
			return (isNegated() != compatible);
		}
	}
}
