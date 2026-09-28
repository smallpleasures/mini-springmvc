package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface ThemeResolver {

	/** 从请求中解析主题名称；无法解析时返回默认主题。 */
	String resolveThemeName(HttpServletRequest request);

	/**
	 * 修改当前请求使用的主题名称。
	 *
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param themeName 新主题名称；传入 {@code null} 或空字符串表示重置
	 * @throws UnsupportedOperationException 当前实现不支持动态修改时抛出
	 */
	void setThemeName(HttpServletRequest request, HttpServletResponse response, String themeName);

}
