package com.yrainy.web.servlet.mvc.method;

import com.yrainy.web.servlet.HandlerAdapter;
import com.yrainy.web.servlet.ModelAndView;
import com.yrainy.web.servlet.support.WebContentGenerator;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 用于适配基于 HandlerMethod 的处理器调用的抽象基类。 */
public abstract class AbstractHandlerMethodAdapter extends WebContentGenerator implements HandlerAdapter, Ordered {

	private int order = Ordered.LOWEST_PRECEDENCE;


	public AbstractHandlerMethodAdapter() {
		// no restriction of HTTP methods by default
		super(false);
	}


	/**
	 * 设置此 HandlerAdapter Bean 的执行顺序。
	 * <p>默认值为 {@code Ordered.LOWEST_PRECEDENCE}，表示未指定优先顺序。
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	/**
	 * 此实现要求处理器为 {@link HandlerMethod}。
	 * @param handler 要检查的处理器实例
	 * @return 此适配器能否处理给定处理器
	 */
	@Override
	public final boolean supports(Object handler) {
		return (handler instanceof HandlerMethod && supportsInternal((HandlerMethod) handler));
	}

	/**
	 * 检查此适配器是否支持给定处理器方法。
	 * @param handlerMethod 要检查的处理器方法
	 * @return 此适配器能否处理给定方法
	 */
	protected abstract boolean supportsInternal(HandlerMethod handlerMethod);

	/**
	 * 此实现要求处理器为 {@link HandlerMethod}。
	 */
	@Override
	public final ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		return handleInternal(request, response, (HandlerMethod) handler);
	}

	/**
	 * 使用给定处理器方法处理请求。该方法应先通过 {@link #supportsInternal(HandlerMethod)} 检查。
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @param handlerMethod 要调用的处理器方法
	 * @return 包含视图名称和模型数据的 ModelAndView；请求已直接处理时返回 {@code null}
	 * @throws Exception 处理过程中发生错误时抛出
	 */
	protected abstract ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception;

	/**
	 * 此实现要求处理器为 {@link HandlerMethod}。
	 */
	@Override
	@SuppressWarnings("deprecation")
	public final long getLastModified(HttpServletRequest request, Object handler) {
		return getLastModifiedInternal(request, (HandlerMethod) handler);
	}

	/**
	 * 与 {@link javax.servlet.http.HttpServlet#getLastModified(HttpServletRequest)} 使用相同的约定。
	 * @param request 当前 HTTP 请求
	 * @param handlerMethod 处理器方法
	 * @return 给定处理器的最后修改时间
	 * @deprecated 自 Spring 5.3.9 起与以下接口一同弃用：
	 * {@link org.springframework.web.servlet.mvc.LastModified}.
	 */
	@Deprecated
	protected abstract long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod);

}
