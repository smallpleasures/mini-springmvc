
package com.yrainy.web.servlet.mvc.method.annotation;

import com.yrainy.web.servlet.HandlerMapping;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.ServletRequestDataBinder;

import javax.servlet.ServletRequest;
import java.util.Map;

/**
 * {@link ServletRequestDataBinder} 的扩展实现，会将 URI 模板变量加入数据绑定值。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 * @see ServletRequestDataBinder
 * @see HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE
 */
public class ExtendedServletRequestDataBinder extends ServletRequestDataBinder {

	/**
	 * 使用默认对象名称创建数据绑定器。
	 * @param target 要绑定的目标对象；仅用于转换普通参数值时可以为 {@code null}
	 * @see #DEFAULT_OBJECT_NAME
	 */
	public ExtendedServletRequestDataBinder(Object target) {
		super(target);
	}

	/**
	 * 创建数据绑定器。
	 * @param target 要绑定的目标对象；仅用于转换普通参数值时可以为 {@code null}
	 * @param objectName 目标对象名称
	 * @see #DEFAULT_OBJECT_NAME
	 */
	public ExtendedServletRequestDataBinder(Object target, String objectName) {
		super(target, objectName);
	}


	/**
	 * 将 URI 模板变量合并到用于数据绑定的属性值中。
	 */
	@Override
	protected void addBindValues(MutablePropertyValues mpvs, ServletRequest request) {
		String attr = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
		@SuppressWarnings("unchecked")
		Map<String, String> uriVars = (Map<String, String>) request.getAttribute(attr);
		if (uriVars != null) {
			uriVars.forEach((name, value) -> {
				if (mpvs.contains(name)) {
					if (logger.isWarnEnabled()) {
						logger.warn("Skipping URI variable '" + name +
								"' because request contains bind value with same name.");
					}
				}
				else {
					mpvs.addPropertyValue(name, value);
				}
			});
		}
	}

}
