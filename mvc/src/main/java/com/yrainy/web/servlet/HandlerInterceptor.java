package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface HandlerInterceptor {

	/**
	 * 在处理器执行前调用。返回 {@code false} 可中止执行链，拦截器此时应自行完成响应。
	 *
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已选定的处理器
	 * @return 返回 {@code true} 继续执行后续拦截器或处理器
	 * @throws Exception 处理过程中发生错误时抛出
	 */
	default boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		return true;
	}

	/**
	 * 处理器成功执行后、视图渲染前调用，可通过视图模型补充模型数据。
	 * 多个拦截器按执行链的逆序调用此方法。
	 *
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已执行的处理器
	 * @param modelAndView 处理器返回的视图模型；直接写响应时可能为 {@code null}
	 * @throws Exception 处理过程中发生错误时抛出
	 */
	default void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
	}

	/**
	 * 请求处理结束后调用，无论处理成功或失败都可在此释放资源。
	 * 仅当本拦截器的 {@code preHandle} 返回 {@code true} 时才会调用；
	 * 多个拦截器按执行链的逆序调用。
	 *
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handler 已执行的处理器
	 * @param ex 处理器执行时抛出的异常；已被异常解析器处理的异常不包含在内
	 * @throws Exception 清理过程中发生错误时抛出
	 */
	default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) throws Exception {
	}

}
