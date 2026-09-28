package com.yrainy.web.servlet;

import java.util.Locale;

public interface ViewResolver {

	/**
	 * 根据名称和地区设置解析视图。
	 * 支持视图解析器链的实现，在找不到视图时应返回 {@code null}。
	 *
	 * @param viewName 视图名称
	 * @param locale 用于解析视图的地区设置
	 * @return 找到的视图；未找到时可返回 {@code null}
	 * @throws Exception 视图无法解析时抛出
	 */
	View resolveViewName(String viewName, Locale locale) throws Exception;

}
