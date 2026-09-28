package com.yrainy.web.servlet.mvc.method;

import com.yrainy.web.servlet.handler.HandlerMethodMappingNamingStrategy;
import org.springframework.web.method.HandlerMethod;

/** 为请求映射生成可读名称；未声明名称时使用控制器类名和方法名。 */
public final class RequestMappingInfoHandlerMethodMappingNamingStrategy
		implements HandlerMethodMappingNamingStrategy<RequestMappingInfo> {

	public static final String SEPARATOR = "#";

	@Override
	public String getName(HandlerMethod handlerMethod, RequestMappingInfo mapping) {
		if (mapping.getName() != null) {
			return mapping.getName();
		}
		return handlerMethod.getBeanType().getSimpleName() + SEPARATOR + handlerMethod.getMethod().getName();
	}
}
