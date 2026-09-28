
package com.yrainy.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
/**
 * 支持在控制器方法执行后、{@code @ResponseBody} 或 {@code ResponseEntity} 响应体写入前修改响应。
 *
 * <p>实现可直接注册到 {@code RequestMappingHandlerAdapter}，也可标注
 * {@code @ControllerAdvice} 以便组件自动发现。
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 * @param <T> 响应体类型
 */
public interface ResponseBodyAdvice<T> {

	/**
	 * 判断当前组件是否支持给定的控制器返回值类型和所选 {@code HttpMessageConverter} 类型。
	 * @param returnType 返回值类型
	 * @param converterType 选中的转换器类型
	 * @return 应调用 {@link #beforeBodyWrite} 时返回 {@code true}
	 */
	boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType);

	/**
	 * 选定 {@code HttpMessageConverter} 后、调用其写入方法前执行。
	 * @param body 要写入的响应体
	 * @param returnType 控制器方法的返回值类型
	 * @param selectedContentType 内容协商选出的媒体类型
	 * @param selectedConverterType 用于写入响应的转换器类型
	 * @param request 当前请求
	 * @param response 当前响应
	 * @return 原响应体或修改后的新响应体
	 */
	T beforeBodyWrite(T body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType,
			ServerHttpRequest request, ServerHttpResponse response);

}
