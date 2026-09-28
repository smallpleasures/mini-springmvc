package com.yrainy.web.servlet.handler;

import org.springframework.web.method.HandlerMethod;

@FunctionalInterface
public interface HandlerMethodMappingNamingStrategy<T> {

	/** 根据处理器方法及其映射生成名称。 */
	String getName(HandlerMethod handlerMethod, T mapping);

}
