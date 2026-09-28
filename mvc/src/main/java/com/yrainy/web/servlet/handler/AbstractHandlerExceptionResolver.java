/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yrainy.web.servlet.handler;

import com.yrainy.web.servlet.HandlerExceptionResolver;
import com.yrainy.web.servlet.ModelAndView;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * {@link HandlerExceptionResolver} 实现的抽象基类。
 *
 * <p>支持通过 {@linkplain #setMappedHandlers 指定处理器} 或
 * {@linkplain #setMappedHandlerClasses 指定处理器类型} 限定解析范围，并实现 {@link Ordered} 接口。
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.0
 */
public abstract class AbstractHandlerExceptionResolver implements HandlerExceptionResolver, Ordered {

	private static final String HEADER_CACHE_CONTROL = "Cache-Control";


    /** 供子类使用的日志记录器。 */
	protected final Log logger = LogFactory.getLog(getClass());

	private int order = Ordered.LOWEST_PRECEDENCE;
	private Set<?> mappedHandlers;
	private Class<?>[] mappedHandlerClasses;
	private Log warnLogger;

	private boolean preventResponseCaching = false;


	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
     * 设置此异常解析器要处理的处理器实例集合。
     * <p>配置后，异常映射和默认错误视图仅适用于指定的处理器。未设置实例或类型时，解析器适用于全部处理器。
	 */
	public void setMappedHandlers(Set<?> mappedHandlers) {
		this.mappedHandlers = mappedHandlers;
	}

	/**
     * 设置此异常解析器适用的处理器类型。指定类型可以是处理器实现的接口或父类。
     * <p>配置后仅处理匹配类型的处理器；未设置实例或类型时，解析器适用于全部处理器。
	 */
	public void setMappedHandlerClasses(Class<?>... mappedHandlerClasses) {
		this.mappedHandlerClasses = mappedHandlerClasses;
	}

	/**
     * 设置警告日志分类。分类名称通过 Commons Logging 交给底层日志实现处理；传入 {@code null} 或空字符串时关闭警告日志。
     * <p>默认不记录警告日志，子类可以更改此默认行为。也可覆盖 {@link #logException} 自定义异常日志。
	 * @see LogFactory#getLog(String)
	 * @see java.util.logging.Logger#getLogger(String)
	 */
	public void setWarnLogCategory(String loggerName) {
		this.warnLogger = (StringUtils.hasLength(loggerName) ? LogFactory.getLog(loggerName) : null);
	}

	/**
     * 设置是否禁止缓存此异常解析器处理的 HTTP 响应。
     * <p>默认值为 {@code false}；设为 {@code true} 后会自动添加禁止缓存的响应头。
	 */
	public void setPreventResponseCaching(boolean preventResponseCaching) {
		this.preventResponseCaching = preventResponseCaching;
	}


	/**
     * 检查此解析器是否适用于当前处理器，再委托给 {@link #doResolveException} 模板方法。
	 */
	@Override
	public ModelAndView resolveException(
			HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

		if (shouldApplyTo(request, handler)) {
			prepareResponse(ex, response);
			ModelAndView result = doResolveException(request, response, handler, ex);
			if (result != null) {
				// 未启用警告日志时，记录调试级别的解析结果。
				if (logger.isDebugEnabled() && (this.warnLogger == null || !this.warnLogger.isWarnEnabled())) {
					logger.debug("Resolved [" + ex + "]" + (result.isEmpty() ? "" : " to " + result));
				}
				// 按 logException 中的配置记录警告日志。
				logException(ex, request);
			}
			return result;
		}
		else {
			return null;
		}
	}

	/**
     * 检查此解析器是否适用于给定处理器。默认实现会检查配置的处理器实例和处理器类型。
     * @param request 当前 HTTP 请求
     * @param handler 已选择的处理器；尚未选择时为 {@code null}
     * @return 是否继续解析当前请求中的异常
	 * @see #setMappedHandlers
	 * @see #setMappedHandlerClasses
	 */
	protected boolean shouldApplyTo(HttpServletRequest request, Object handler) {
		if (handler != null) {
			if (this.mappedHandlers != null && this.mappedHandlers.contains(handler)) {
				return true;
			}
			if (this.mappedHandlerClasses != null) {
				for (Class<?> handlerClass : this.mappedHandlerClasses) {
					if (handlerClass.isInstance(handler)) {
						return true;
					}
				}
			}
		}
		return !hasHandlerMappings();
	}

	/**
     * 是否通过 {@link #setMappedHandlers(Set)} 或 {@link #setMappedHandlerClasses(Class[])} 配置了处理器范围。
	 * @since 5.3
	 */
	protected boolean hasHandlerMappings() {
		return (this.mappedHandlers != null || this.mappedHandlerClasses != null);
	}

	/**
     * 已通过 {@link #setWarnLogCategory} 启用警告日志时，记录给定异常。
     * <p>调用 {@link #buildLogMessage} 生成日志内容。
     * @param ex 处理器执行期间抛出的异常
     * @param request 当前 HTTP 请求，可用于读取请求信息
	 * @see #setWarnLogCategory
	 * @see #buildLogMessage
	 * @see Log#warn(Object, Throwable)
	 */
	protected void logException(Exception ex, HttpServletRequest request) {
		if (this.warnLogger != null && this.warnLogger.isWarnEnabled()) {
			this.warnLogger.warn(buildLogMessage(ex, request));
		}
	}

	/**
     * 为处理请求期间发生的异常生成日志内容。
     * @param ex 处理器执行期间抛出的异常
     * @param request 当前 HTTP 请求，可用于读取请求信息
     * @return 要记录的日志内容
	 */
	protected String buildLogMessage(Exception ex, HttpServletRequest request) {
		return "Resolved [" + ex + "]";
	}

	/**
     * 根据异常处理配置准备 HTTP 响应。
     * <p>启用 {@link #setPreventResponseCaching} 后，默认实现会禁止缓存响应。
     * @param ex 处理器执行期间抛出的异常
     * @param response 当前 HTTP 响应
	 * @see #preventCaching
	 */
	protected void prepareResponse(Exception ex, HttpServletResponse response) {
		if (this.preventResponseCaching) {
			preventCaching(response);
		}
	}

	/**
     * 添加 {@code Cache-Control: no-store} 响应头，禁止缓存响应。
     * @param response 当前 HTTP 响应
	 */
	protected void preventCaching(HttpServletResponse response) {
		response.addHeader(HEADER_CACHE_CONTROL, "no-store");
	}


	/**
     * 实际处理处理器执行期间抛出的异常，并在需要时返回表示错误页面的 {@link ModelAndView}。
     * <p>子类可覆盖此方法处理特定异常。调用前已检查解析器是否适用，因此实现可以直接处理异常。
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 已选择的处理器；尚未选择时为 {@code null}
     * @param ex 处理器执行期间抛出的异常
     * @return 用于转发的视图模型；返回 {@code null} 时由解析链继续处理
	 */
	protected abstract ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex);

}
