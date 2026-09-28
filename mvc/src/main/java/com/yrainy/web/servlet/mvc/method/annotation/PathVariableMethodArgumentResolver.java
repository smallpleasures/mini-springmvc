package com.yrainy.web.servlet.mvc.method.annotation;

import com.yrainy.web.servlet.HandlerMapping;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.NestedServletException;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/** 从映射器保存的 URI 变量中解析 {@code @PathVariable} 参数。 */
public class PathVariableMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		PathVariable annotation = parameter.getParameterAnnotation(PathVariable.class);
		return annotation != null && !Map.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		PathVariable annotation = parameter.getParameterAnnotation(PathVariable.class);
		if (annotation == null) {
			throw new IllegalStateException("@PathVariable annotation is missing");
		}
		String name = (annotation.name().isEmpty() ? annotation.value() : annotation.name());
		if (name.isEmpty()) {
			name = parameter.getParameterName();
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalStateException("Path variable name is not available; declare it in @PathVariable");
		}

		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Map<String, String> uriVariables = (request != null ?
				(Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) : null);
		Object value = (uriVariables != null ? uriVariables.get(name) : null);
		if (value == null) {
			if (annotation.required()) {
				throw new MissingPathVariableException(name, parameter);
			}
			return Optional.class.isAssignableFrom(parameter.getParameterType()) ? Optional.empty() : null;
		}
		if (Optional.class.isAssignableFrom(parameter.getParameterType())) {
			return Optional.of(value);
		}
		if (binderFactory != null) {
			return binderFactory.createBinder(webRequest, null, name)
					.convertIfNecessary(value, parameter.getParameterType(), parameter);
		}
		return value;
	}
}
