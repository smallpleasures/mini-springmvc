
package com.yrainy.web.servlet.mvc.condition;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 描述媒体类型表达式的接口，例如 {@code "text/plain"} 和 {@code "!text/plain"}；
 * 表达式由 {@code @RequestMapping} 的 consumes 和 produces 条件定义。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 *
 * @see RequestMapping#consumes()
 * @see RequestMapping#produces()
 */
public interface MediaTypeExpression {

	MediaType getMediaType();

	boolean isNegated();

}
