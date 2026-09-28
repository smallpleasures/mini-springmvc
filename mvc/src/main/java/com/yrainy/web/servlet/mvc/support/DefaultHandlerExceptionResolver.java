
package com.yrainy.web.servlet.mvc.support;

import com.yrainy.web.servlet.ModelAndView;
import com.yrainy.web.servlet.NoHandlerFoundException;
import com.yrainy.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * {@link org.springframework.web.servlet.HandlerExceptionResolver} 的默认实现，
 * 负责将常见请求处理异常转换为对应的 HTTP 状态码。
 *
 * <p>该解析器默认注册在 MiniDispatcherServlet 中，可处理请求方法或媒体类型不匹配、
 * 参数绑定失败、消息转换失败、未找到处理器及异步请求超时等异常。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
 */
public class DefaultHandlerExceptionResolver extends AbstractHandlerExceptionResolver {

	/**
	 * 请求未找到映射处理器时使用的日志分类。
	 * @see #pageNotFoundLogger
	 */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * 请求未找到映射处理器时使用的附加日志记录器。
	 * @see #PAGE_NOT_FOUND_LOG_CATEGORY
	 */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);


	/**
	 * 将解析顺序设为 {@link #LOWEST_PRECEDENCE}，使其默认在其他解析器之后执行。
	 */
	public DefaultHandlerExceptionResolver() {
		setOrder(Ordered.LOWEST_PRECEDENCE);
		setWarnLogCategory(getClass().getName());
	}


	@Override
	protected ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

		try {
			if (ex instanceof HttpRequestMethodNotSupportedException) {
				return handleHttpRequestMethodNotSupported(
						(HttpRequestMethodNotSupportedException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMediaTypeNotSupportedException) {
				return handleHttpMediaTypeNotSupported(
						(HttpMediaTypeNotSupportedException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMediaTypeNotAcceptableException) {
				return handleHttpMediaTypeNotAcceptable(
						(HttpMediaTypeNotAcceptableException) ex, request, response, handler);
			}
			else if (ex instanceof MissingPathVariableException) {
				return handleMissingPathVariable(
						(MissingPathVariableException) ex, request, response, handler);
			}
			else if (ex instanceof MissingServletRequestParameterException) {
				return handleMissingServletRequestParameter(
						(MissingServletRequestParameterException) ex, request, response, handler);
			}
			else if (ex instanceof ServletRequestBindingException) {
				return handleServletRequestBindingException(
						(ServletRequestBindingException) ex, request, response, handler);
			}
			else if (ex instanceof ConversionNotSupportedException) {
				return handleConversionNotSupported(
						(ConversionNotSupportedException) ex, request, response, handler);
			}
			else if (ex instanceof TypeMismatchException) {
				return handleTypeMismatch(
						(TypeMismatchException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMessageNotReadableException) {
				return handleHttpMessageNotReadable(
						(HttpMessageNotReadableException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMessageNotWritableException) {
				return handleHttpMessageNotWritable(
						(HttpMessageNotWritableException) ex, request, response, handler);
			}
			else if (ex instanceof MethodArgumentNotValidException) {
				return handleMethodArgumentNotValidException(
						(MethodArgumentNotValidException) ex, request, response, handler);
			}
			else if (ex instanceof MissingServletRequestPartException) {
				return handleMissingServletRequestPartException(
						(MissingServletRequestPartException) ex, request, response, handler);
			}
			else if (ex instanceof BindException) {
				return handleBindException((BindException) ex, request, response, handler);
			}
			else if (ex instanceof NoHandlerFoundException) {
				return handleNoHandlerFoundException(
						(NoHandlerFoundException) ex, request, response, handler);
			}
			else if (ex instanceof AsyncRequestTimeoutException) {
				return handleAsyncRequestTimeoutException(
						(AsyncRequestTimeoutException) ex, request, response, handler);
			}
		}
		catch (Exception handlerEx) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failure while trying to resolve exception [" + ex.getClass().getName() + "]", handlerEx);
			}
		}
		return null;
	}

	/**
	 * 处理请求路径存在映射、但当前 HTTP 方法不受支持的情况，默认发送 405 响应并设置 {@code Allow} 响应头。
	 * @param ex 待处理的 HTTP 方法不支持异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器；尚未选择时为 {@code null}
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 调用 {@link HttpServletResponse#sendError} 失败时抛出
	 */
	protected ModelAndView handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		String[] supportedMethods = ex.getSupportedMethods();
		if (supportedMethods != null) {
			response.setHeader("Allow", StringUtils.arrayToDelimitedString(supportedMethods, ", "));
		}
		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, ex.getMessage());
		return new ModelAndView();
	}

	/**
	 * 处理请求内容没有合适消息转换器的情况，默认发送 415 响应并设置支持的媒体类型。
	 * @param ex 待处理的媒体类型不支持异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 调用 {@link HttpServletResponse#sendError} 失败时抛出
	 */
	protected ModelAndView handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		List<MediaType> mediaTypes = ex.getSupportedMediaTypes();
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			response.setHeader("Accept", MediaType.toString(mediaTypes));
			if (request.getMethod().equals("PATCH")) {
				response.setHeader("Accept-Patch", MediaType.toString(mediaTypes));
			}
		}
		response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
		return new ModelAndView();
	}

	/**
	 * 处理客户端 {@code Accept} 请求头无法接受任何可用媒体类型的情况，默认发送 406 响应。
	 * @param ex 待处理的媒体类型不可接受异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 调用 {@link HttpServletResponse#sendError} 失败时抛出
	 */
	protected ModelAndView handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
		return new ModelAndView();
	}

	/**
	 * 处理控制器声明的路径变量未能从请求 URI 中解析出来的情况，默认发送 500 响应。
	 * @param ex 待处理的路径变量缺失异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 调用 {@link HttpServletResponse#sendError} 失败时抛出
	 * @since 4.2
	 */
	protected ModelAndView handleMissingPathVariable(MissingPathVariableException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
		return new ModelAndView();
	}

	/**
	 * 处理必需请求参数缺失的情况，默认发送 400 响应。
	 * @param ex 待处理的请求参数缺失异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 调用 {@link HttpServletResponse#sendError} 失败时抛出
	 */
	protected ModelAndView handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
		return new ModelAndView();
	}

	/**
	 * 处理无法恢复的请求绑定异常，例如必需请求头或 Cookie 缺失；默认发送 400 响应。
	 * @param ex 待处理的绑定异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 调用 {@link HttpServletResponse#sendError} 失败时抛出
	 */
	protected ModelAndView handleServletRequestBindingException(ServletRequestBindingException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
		return new ModelAndView();
	}

	/**
	 * 处理 {@link org.springframework.web.bind.WebDataBinder} 无法执行类型转换的情况，默认发送 500 响应。
	 * @param ex 待处理的转换不支持异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleConversionNotSupported(ConversionNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		sendServerError(ex, request, response);
		return new ModelAndView();
	}

	/**
	 * 处理 {@link org.springframework.web.bind.WebDataBinder} 类型转换失败的情况，默认发送 400 响应。
	 * @param ex 待处理的类型不匹配异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleTypeMismatch(TypeMismatchException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * 处理消息转换器无法读取 HTTP 请求体的情况，默认发送 400 响应。
	 * @param ex 待处理的请求消息不可读异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * 处理消息转换器无法写入 HTTP 响应体的情况，默认发送 500 响应。
	 * @param ex 待处理的响应消息不可写异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleHttpMessageNotWritable(HttpMessageNotWritableException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		sendServerError(ex, request, response);
		return new ModelAndView();
	}

	/**
	 * 处理 {@code @Valid} 参数校验失败的情况，例如 {@link RequestBody} 或 {@link RequestPart} 参数无效。
	 * <p>默认向客户端发送 400 响应。
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * 处理必需的 {@linkplain RequestPart @RequestPart}、{@link MultipartFile} 或
	 * {@code javax.servlet.http.Part} 参数缺失的情况，默认发送 400 响应。
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleMissingServletRequestPartException(MissingServletRequestPartException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
		return new ModelAndView();
	}

	/**
	 * 处理 {@linkplain ModelAttribute @ModelAttribute} 参数绑定或校验失败，且后续没有
	 * {@link BindingResult} 参数接收错误信息的情况；默认发送 400 响应。
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 */
	protected ModelAndView handleBindException(BindException ex, HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * 处理分发过程中未找到处理器的情况，默认记录警告并发送 404 响应。
	 * @param ex 待处理的未找到处理器异常
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器；尚未选择时为 {@code null}
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 * @since 4.0
	 */
	protected ModelAndView handleNoHandlerFoundException(NoHandlerFoundException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		pageNotFoundLogger.warn(ex.getMessage());
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
		return new ModelAndView();
	}

	/**
	 * 处理异步请求超时的情况，默认发送 503 响应。
	 * @param ex 待处理的 {@link AsyncRequestTimeoutException}
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选择的处理器；尚未选择时为 {@code null}
	 * @return 空的 ModelAndView，表示异常已处理
	 * @throws IOException 发送错误响应失败时抛出
	 * @since 4.2.8
	 */
	protected ModelAndView handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex,
			HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

		if (!response.isCommitted()) {
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
		else {
			logger.warn("Async request timed out");
		}
		return new ModelAndView();
	}

	/**
	 * 发送服务器错误响应，将状态码设为 500，并把异常保存到
	 * {@code javax.servlet.error.exception} 请求属性中。
	 */
	protected void sendServerError(Exception ex, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		request.setAttribute("javax.servlet.error.exception", ex);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

}
