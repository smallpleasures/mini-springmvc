
package com.yrainy.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.ServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * 解析 Servlet 响应相关的方法参数，支持以下类型：
 * <ul>
 * <li>{@link ServletResponse}</li>
 * <li>{@link OutputStream}</li>
 * <li>{@link Writer}</li>
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletResponseMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		return (ServletResponse.class.isAssignableFrom(paramType) ||
				OutputStream.class.isAssignableFrom(paramType) ||
				Writer.class.isAssignableFrom(paramType));
	}

	/**
     * 将 {@link ModelAndViewContainer#setRequestHandled(boolean)} 设为 {@code false}，
     * 表示方法参数可直接访问响应；若目标方法随后返回 {@code null}，则视为请求已直接处理。
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		if (mavContainer != null) {
			mavContainer.setRequestHandled(true);
		}

		Class<?> paramType = parameter.getParameterType();

        // 解析 ServletResponse 或 HttpServletResponse。
		if (ServletResponse.class.isAssignableFrom(paramType)) {
			return resolveNativeResponse(webRequest, paramType);
		}

        // 其余参数类型均需先取得 ServletResponse。
		return resolveArgument(paramType, resolveNativeResponse(webRequest, ServletResponse.class));
	}

	private <T> T resolveNativeResponse(NativeWebRequest webRequest, Class<T> requiredType) {
		T nativeResponse = webRequest.getNativeResponse(requiredType);
		if (nativeResponse == null) {
			throw new IllegalStateException(
					"Current response is not of type [" + requiredType.getName() + "]: " + webRequest);
		}
		return nativeResponse;
	}

	private Object resolveArgument(Class<?> paramType, ServletResponse response) throws IOException {
		if (OutputStream.class.isAssignableFrom(paramType)) {
			return response.getOutputStream();
		}
		else if (Writer.class.isAssignableFrom(paramType)) {
			return response.getWriter();
		}

        // 参数类型已通过 supportsParameter 检查，正常情况下不会进入此处。
		throw new UnsupportedOperationException("Unknown parameter type: " + paramType);
	}

}
