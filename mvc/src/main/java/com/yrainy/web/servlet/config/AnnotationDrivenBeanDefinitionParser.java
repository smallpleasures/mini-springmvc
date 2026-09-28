package com.yrainy.web.servlet.config;

import com.yrainy.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/** 解析 {@code <mvc:annotation-driven>} 并注册注解式请求处理所需的组件。 */
class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {

	@Override
	public BeanDefinition parse(Element element, ParserContext context) {
		MvcNamespaceUtils.registerDefaultComponents(context, context.extractSource(element));

		String handlerMappingName = RequestMappingHandlerMapping.class.getName();
		BeanDefinition handlerMapping = context.getRegistry().getBeanDefinition(handlerMappingName);
		handlerMapping.getPropertyValues().add("order", 0);

		return null;
	}
}
