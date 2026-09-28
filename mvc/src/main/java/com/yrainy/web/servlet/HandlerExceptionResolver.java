package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface HandlerExceptionResolver {

	/**
	 * 尝试处理控制器执行期间发生的异常，并在需要时返回错误视图。
	 * 返回空视图模型表示异常已处理完成，但无需渲染视图，例如只设置状态码。
	 *
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已执行的处理器；异常发生时尚未选出处理器则为 {@code null}
	 * @param ex 控制器执行期间发生的异常
	 * @return 用于转发的视图模型；返回 {@code null} 时由后续解析器继续处理
	 */
	ModelAndView resolveException(
			HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex);

}
