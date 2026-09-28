package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

public interface LocaleResolver {

	/** 从请求中解析地区设置；无法解析时返回默认值。 */
	Locale resolveLocale(HttpServletRequest request);

	/**
	 * 修改当前请求使用的地区设置。
	 *
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param locale 新的地区设置；传入 {@code null} 表示清除
	 * @throws UnsupportedOperationException 当前实现不支持动态修改时抛出
	 */
	void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale);

}
