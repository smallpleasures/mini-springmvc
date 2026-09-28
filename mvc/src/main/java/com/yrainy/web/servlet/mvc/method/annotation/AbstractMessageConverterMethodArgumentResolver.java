
package com.yrainy.web.servlet.mvc.method.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.*;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 参数解析器的抽象基类，通过 {@link HttpMessageConverter} 从请求体读取方法参数。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractMessageConverterMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private static final Set<HttpMethod> SUPPORTED_METHODS =
			EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

	private static final Object NO_VALUE = new Object();


	protected final Log logger = LogFactory.getLog(getClass());

	protected final List<HttpMessageConverter<?>> messageConverters;

	private final RequestResponseBodyAdviceChain advice;


	/**
	 * 仅使用消息转换器创建解析器。
	 */
	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> converters) {
		this(converters, null);
	}

	/**
	 * 使用消息转换器和请求体、响应体通知组件创建解析器。
	 * @since 4.2
	 */
	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> converters,
                                                          List<Object> requestResponseBodyAdvice) {

		Assert.notEmpty(converters, "'messageConverters' must not be empty");
		this.messageConverters = converters;
		this.advice = new RequestResponseBodyAdviceChain(requestResponseBodyAdvice);
	}


	/**
	 * 返回已配置的请求体和响应体通知链。通知实例可能被包装为
	 * {@link org.springframework.web.method.ControllerAdviceBean}。
	 */
	RequestResponseBodyAdviceChain getAdvice() {
		return this.advice;
	}

	/**
	 * 从给定请求中读取内容，并创建指定类型的方法参数值。
	 * @param <T> 要创建的参数值类型
	 * @param webRequest 当前请求
	 * @param parameter 方法参数描述符，可以为 {@code null}
	 * @param paramType 要创建的参数值类型
	 * @return 创建得到的方法参数值
	 * @throws IOException 读取请求失败时抛出
	 * @throws HttpMediaTypeNotSupportedException 找不到合适的消息转换器时抛出
	 */
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest, MethodParameter parameter,
			Type paramType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {

		HttpInputMessage inputMessage = createInputMessage(webRequest);
		return readWithMessageConverters(inputMessage, parameter, paramType);
	}

	/**
	 * 从给定 HttpInputMessage 中读取内容，并创建指定类型的方法参数值。
	 * @param <T> 要创建的参数值类型
	 * @param inputMessage 表示当前请求的 HTTP 输入消息
	 * @param parameter 方法参数描述符
	 * @param targetType 目标类型，不一定与方法参数类型相同，例如 {@code HttpEntity<String>}
	 * @return 创建得到的方法参数值
	 * @throws IOException 读取请求失败时抛出
	 * @throws HttpMediaTypeNotSupportedException 找不到合适的消息转换器时抛出
	 */
	@SuppressWarnings("unchecked")
	protected <T> Object readWithMessageConverters(HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {

		MediaType contentType;
		boolean noContentType = false;
		try {
			contentType = inputMessage.getHeaders().getContentType();
		}
		catch (InvalidMediaTypeException ex) {
			throw new HttpMediaTypeNotSupportedException(ex.getMessage());
		}
		if (contentType == null) {
			noContentType = true;
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}

		Class<?> contextClass = parameter.getContainingClass();
		Class<T> targetClass = (targetType instanceof Class ? (Class<T>) targetType : null);
		if (targetClass == null) {
			ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
			targetClass = (Class<T>) resolvableType.resolve();
		}

		HttpMethod httpMethod = (inputMessage instanceof HttpRequest ? ((HttpRequest) inputMessage).getMethod() : null);
		Object body = NO_VALUE;

		EmptyBodyCheckingHttpInputMessage message;
		try {
			message = new EmptyBodyCheckingHttpInputMessage(inputMessage);

			for (HttpMessageConverter<?> converter : this.messageConverters) {
				Class<HttpMessageConverter<?>> converterType = (Class<HttpMessageConverter<?>>) converter.getClass();
				GenericHttpMessageConverter<?> genericConverter =
						(converter instanceof GenericHttpMessageConverter ? (GenericHttpMessageConverter<?>) converter : null);
				if (genericConverter != null ? genericConverter.canRead(targetType, contextClass, contentType) :
						(targetClass != null && converter.canRead(targetClass, contentType))) {
					if (message.hasBody()) {
						HttpInputMessage msgToUse =
								getAdvice().beforeBodyRead(message, parameter, targetType, converterType);
						body = (genericConverter != null ? genericConverter.read(targetType, contextClass, msgToUse) :
								((HttpMessageConverter<T>) converter).read(targetClass, msgToUse));
						body = getAdvice().afterBodyRead(body, msgToUse, parameter, targetType, converterType);
					}
					else {
						body = getAdvice().handleEmptyBody(null, message, parameter, targetType, converterType);
					}
					break;
				}
			}
		}
		catch (IOException ex) {
			throw new HttpMessageNotReadableException("I/O error while reading input message", ex, inputMessage);
		}

		if (body == NO_VALUE) {
			if (httpMethod == null || !SUPPORTED_METHODS.contains(httpMethod) ||
					(noContentType && !message.hasBody())) {
				return null;
			}
			throw new HttpMediaTypeNotSupportedException(contentType,
					getSupportedMediaTypes(targetClass != null ? targetClass : Object.class));
		}

		MediaType selectedContentType = contentType;
		Object theBody = body;
		LogFormatUtils.traceDebug(logger, traceOn -> {
			String formatted = LogFormatUtils.formatValue(theBody, !traceOn);
			return "Read \"" + selectedContentType + "\" to [" + formatted + "]";
		});

		return body;
	}

	/**
	 * 根据给定的 {@link NativeWebRequest} 创建 {@link HttpInputMessage}。
	 * @param webRequest 用于创建输入消息的 Web 请求
	 * @return 创建的输入消息
	 */
	protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		Assert.state(servletRequest != null, "No HttpServletRequest");
		return new ServletServerHttpRequest(servletRequest);
	}

	/**
	 * 条件适用时校验数据绑定目标。
	 * <p>默认实现会识别 {@code @javax.validation.Valid}、Spring 的
	 * {@link org.springframework.validation.annotation.Validated}，以及名称以 Valid 开头的自定义注解。
	 * @param binder 用于校验的 DataBinder
	 * @param parameter 方法参数描述符
	 * @since 4.1.5
	 * @see #isBindExceptionRequired
	 */
	protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
		Annotation[] annotations = parameter.getParameterAnnotations();
		for (Annotation ann : annotations) {
			Object[] validationHints = ValidationAnnotationUtils.determineValidationHints(ann);
			if (validationHints != null) {
				binder.validate(validationHints);
				break;
			}
		}
	}

	/**
	 * 判断校验失败时是否抛出绑定异常。
	 * @param binder 用于数据绑定的 DataBinder
	 * @param parameter 方法参数描述符
	 * @return 下一个方法参数不是 {@link Errors} 类型时返回 {@code true}
	 * @since 4.1.5
	 */
	protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
		return !hasBindingResult;
	}

	/**
	 * 返回所有消息转换器支持的媒体类型，并通过 {@link MediaType#sortBySpecificity(List)} 按具体程度排序。
	 * @since 5.3.4
	 */
	protected List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
		Set<MediaType> mediaTypeSet = new LinkedHashSet<>();
		for (HttpMessageConverter<?> converter : this.messageConverters) {
			mediaTypeSet.addAll(converter.getSupportedMediaTypes(clazz));
		}
		List<MediaType> result = new ArrayList<>(mediaTypeSet);
		MediaType.sortBySpecificity(result);
		return result;
	}

	/**
	 * 必要时将已解析参数转换为方法参数需要的形式。
	 * @param arg 已解析的参数
	 * @param parameter 方法参数描述符
	 * @return 转换后的参数；无需转换时返回原参数
	 * @since 4.3.5
	 */
	protected Object adaptArgumentIfNecessary(Object arg, MethodParameter parameter) {
		if (parameter.getParameterType() == Optional.class) {
			if (arg == null || (arg instanceof Collection && ((Collection<?>) arg).isEmpty()) ||
					(arg instanceof Object[] && ((Object[]) arg).length == 0)) {
				return Optional.empty();
			}
			else {
				return Optional.of(arg);
			}
		}
		return arg;
	}


	private static class EmptyBodyCheckingHttpInputMessage implements HttpInputMessage {

		private final HttpHeaders headers;
		private final InputStream body;

		public EmptyBodyCheckingHttpInputMessage(HttpInputMessage inputMessage) throws IOException {
			this.headers = inputMessage.getHeaders();
			InputStream inputStream = inputMessage.getBody();
			if (inputStream.markSupported()) {
				inputStream.mark(1);
				this.body = (inputStream.read() != -1 ? inputStream : null);
				inputStream.reset();
			}
			else {
				PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
				int b = pushbackInputStream.read();
				if (b == -1) {
					this.body = null;
				}
				else {
					this.body = pushbackInputStream;
					pushbackInputStream.unread(b);
				}
			}
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public InputStream getBody() {
			return (this.body != null ? this.body : StreamUtils.emptyInput());
		}

		public boolean hasBody() {
			return (this.body != null);
		}
	}

}
