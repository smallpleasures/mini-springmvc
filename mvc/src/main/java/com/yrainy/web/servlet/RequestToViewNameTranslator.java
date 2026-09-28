package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;

public interface RequestToViewNameTranslator {

	/**
	 * 根据当前请求生成默认视图名称。
	 *
	 * @param request 当前 HTTP 请求
	 * @return 视图名称；无法生成默认名称时返回 {@code null}
	 * @throws Exception 视图名称解析失败时抛出
	 */
	String getViewName(HttpServletRequest request) throws Exception;

}
