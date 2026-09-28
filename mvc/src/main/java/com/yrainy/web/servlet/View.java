package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface View {

	/** 保存响应状态码的请求属性名。 */
	String RESPONSE_STATUS_ATTRIBUTE = View.class.getName() + ".responseStatus";

	/** 保存 URI 模板变量及其转换后值的请求属性名。 */
	String PATH_VARIABLES = View.class.getName() + ".pathVariables";

	/** 保存内容协商最终选定媒体类型的请求属性名。 */
	String SELECTED_CONTENT_TYPE = View.class.getName() + ".selectedContentType";


	/** 返回视图预先确定的媒体类型；尚未确定时返回 {@code null}。 */
	default String getContentType() {
		return null;
	}

	/**
	 * 使用给定模型渲染视图。实现通常先准备请求属性，再生成响应内容。
	 *
	 * @param model 模型名称和值；模型为空时可以为 {@code null}
	 * @param request 当前 HTTP 请求
	 * @param response 当前 HTTP 响应
	 * @throws Exception 渲染失败时抛出
	 */
	void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
			throws Exception;

}
