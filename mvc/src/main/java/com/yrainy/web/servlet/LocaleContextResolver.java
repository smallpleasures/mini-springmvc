package com.yrainy.web.servlet;

import org.springframework.context.i18n.LocaleContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface LocaleContextResolver extends LocaleResolver {

	/**
	 * 从请求中解析地区上下文，其中可以包含地区和时区信息。
	 * 应用代码可通过 Spring 的请求上下文工具读取解析结果。
	 *
	 * @param request 当前请求
	 * @return 当前地区上下文
	 * @see #resolveLocale(HttpServletRequest)
	 */
	LocaleContext resolveLocaleContext(HttpServletRequest request);

	/**
	 * 修改当前请求使用的地区上下文，可同时设置地区与时区。
	 *
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param localeContext 新的地区上下文；传入 {@code null} 表示清除
	 * @throws UnsupportedOperationException 当前实现不支持动态修改时抛出
	 * @see #setLocale(HttpServletRequest, HttpServletResponse, Locale)
	 */
	void setLocaleContext(HttpServletRequest request, HttpServletResponse response,
			LocaleContext localeContext);

}
