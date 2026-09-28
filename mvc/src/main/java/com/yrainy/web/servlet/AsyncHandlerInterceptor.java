package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface AsyncHandlerInterceptor extends HandlerInterceptor {

	/**
	 * 处理器异步执行时调用此方法，替代 {@code postHandle} 和 {@code afterCompletion}。
	 * 可在此处清理线程局部变量；不要修改会干扰异步处理的请求或响应状态。
	 *
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param handler 启动异步处理的处理器
	 * @throws Exception 执行过程中发生错误时抛出
	 */
	default void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
												Object handler) throws Exception {
	}

}
