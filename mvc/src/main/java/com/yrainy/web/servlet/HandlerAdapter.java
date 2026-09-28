package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface HandlerAdapter {

	/** 判断此适配器是否支持给定处理器。 */
	boolean supports(Object handler);

	/** 调用处理器并返回视图模型；直接写入响应时可以返回 {@code null}。 */
	ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;

	/**
	 * 获取处理器对应资源的最后修改时间，语义与 Servlet 的
	 * {@code getLastModified} 方法相同。不支持时返回 {@code -1}。
	 *
	 * @param request 当前 HTTP 请求
	 * @param handler 待查询的处理器
	 * @return 资源的最后修改时间
	 * @deprecated 该能力已弃用
	 */
	@Deprecated
	long getLastModified(HttpServletRequest request, Object handler);

}
