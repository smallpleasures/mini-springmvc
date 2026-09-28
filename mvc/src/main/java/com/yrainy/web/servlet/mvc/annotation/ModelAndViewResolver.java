package com.yrainy.web.servlet.mvc.annotation;

import com.yrainy.web.servlet.ModelAndView;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.request.NativeWebRequest;

import java.lang.reflect.Method;

/** 根据处理器方法的返回值解析视图模型。 */
public interface ModelAndViewResolver {

	/**
	 * 解析器无法处理给定方法返回值时返回的标记对象。
	 */
	ModelAndView UNRESOLVED = new ModelAndView();


	ModelAndView resolveModelAndView(Method handlerMethod, Class<?> handlerType,
									 Object returnValue, ExtendedModelMap implicitModel, NativeWebRequest webRequest);

}
