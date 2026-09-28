
package com.yrainy.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 支持在读取并转换请求体之前修改请求，也支持在转换结果作为 {@code @RequestBody} 或
 * {@code HttpEntity} 参数传入控制器方法之前处理该对象。
 *
 * <p>实现可直接注册到 {@code RequestMappingHandlerAdapter}，也可标注
 * {@code @ControllerAdvice} 以便自动发现。
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public interface RequestBodyAdvice {

	/**
	 * 首先调用此方法，判断当前通知是否适用。
	 * @param methodParameter 方法参数
	 * @param targetType 目标类型，不一定与方法参数类型相同，例如 {@code HttpEntity<String>}
	 * @param converterType 选中的转换器类型
	 * @return 是否调用此通知
	 */
	boolean supports(MethodParameter methodParameter, Type targetType,
			Class<? extends HttpMessageConverter<?>> converterType);

	/**
	 * 在读取和转换请求体之前调用。
	 * @param inputMessage 当前请求
	 * @param parameter 目标方法参数
	 * @param targetType 目标类型，不一定与方法参数类型相同，例如 {@code HttpEntity<String>}
	 * @param converterType 用于读取请求体的转换器类型
	 * @return 当前输入消息或新的输入消息，不得返回 {@code null}
	 */
	HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException;

	/**
	 * 请求体转换为对象后最后调用。
	 * @param body 首个通知执行前由转换器生成的对象
	 * @param inputMessage 当前请求
	 * @param parameter 目标方法参数
	 * @param targetType 目标类型，不一定与方法参数类型相同，例如 {@code HttpEntity<String>}
	 * @param converterType 用于读取请求体的转换器类型
	 * @return 原对象或新的对象
	 */
	Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType);

	/**
	 * 请求体为空时最后调用。
	 * @param body 首个通知执行前通常为 {@code null}
	 * @param inputMessage 当前请求
	 * @param parameter 方法参数
	 * @param targetType 目标类型，不一定与方法参数类型相同，例如 {@code HttpEntity<String>}
	 * @param converterType 选中的转换器类型
	 * @return 要使用的参数值；返回 {@code null} 且参数为必需时可能抛出 {@code HttpMessageNotReadableException}
	 */
	Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType);


}
