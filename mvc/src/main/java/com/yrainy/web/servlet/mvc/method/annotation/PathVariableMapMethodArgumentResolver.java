package com.yrainy.web.servlet.mvc.method.annotation;

import com.yrainy.web.servlet.HandlerMapping;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将本次匹配得到的全部 URI 变量注入带有 {@code @PathVariable} 的 Map 参数。 */
public class PathVariableMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		PathVariable annotation = parameter.getParameterAnnotation(PathVariable.class);
		return annotation != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
				annotation.name().isEmpty() && annotation.value().isEmpty();
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {

		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		if (request == null) {
			return Collections.emptyMap();
		}
		Map<String, String> variables = (Map<String, String>)
				request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return (variables != null ? new LinkedHashMap<>(variables) : Collections.emptyMap());
	}
}
