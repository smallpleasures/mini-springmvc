package com.yrainy.web.servlet;

import javax.servlet.http.HttpServletRequest;

public interface HandlerMapping {

	/** 保存本次请求最佳匹配处理器的请求属性名。 */
	String BEST_MATCHING_HANDLER_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingHandler";

	/** 保存处理器映射范围内路径的请求属性名；并非所有映射实现都会提供此属性。 */
	String PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE = HandlerMapping.class.getName() + ".pathWithinHandlerMapping";

	/** 保存本次请求最佳匹配路径模式的请求属性名；并非所有映射实现都会提供此属性。 */
	String BEST_MATCHING_PATTERN_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingPattern";

	/** 标记是否检查控制器类级别映射的布尔型请求属性名。 */
	String INTROSPECT_TYPE_LEVEL_MAPPING = HandlerMapping.class.getName() + ".introspectTypeLevelMapping";

	/** 保存 URI 模板变量名和值映射的请求属性名；并非所有映射实现都会提供此属性。 */
	String URI_TEMPLATE_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".uriTemplateVariables";

	/** 保存 URI 矩阵变量映射的请求属性名；是否提供取决于映射器配置。 */
	String MATRIX_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".matrixVariables";

	/** 保存当前处理器可生成媒体类型集合的请求属性名；并非所有映射实现都会提供此属性。 */
	String PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE = HandlerMapping.class.getName() + ".producibleMediaTypes";


	/** 当前映射器启用解析后路径模式时返回 {@code true}。 */
	default boolean usesPathPatterns() {
		return false;
	}

	/**
	 * 为当前请求查找处理器及其拦截器。具体选择方式由实现决定。
	 * 未找到映射时返回 {@code null}；分发器会继续查询其他映射器。
	 *
	 * @param request 当前 HTTP 请求
	 * @return 包含处理器和拦截器的执行链；未找到时返回 {@code null}
	 * @throws Exception 查找过程中发生内部错误时抛出
	 */
	HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception;

}
