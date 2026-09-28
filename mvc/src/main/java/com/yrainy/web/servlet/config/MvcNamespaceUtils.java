package com.yrainy.web.servlet.config;

import com.yrainy.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import com.yrainy.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.yrainy.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import com.yrainy.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
/** MVC 命名空间中用于注册基础组件的工具类。 */
public abstract class MvcNamespaceUtils {

	/** 注册注解映射、方法适配器和默认异常解析器。 */
	public static void registerDefaultComponents(ParserContext context, Object source) {
		registerIfMissing(context, RequestMappingHandlerMapping.class, source);
		registerIfMissing(context, RequestMappingHandlerAdapter.class, source);
		registerIfMissing(context, ExceptionHandlerExceptionResolver.class, source);
		registerIfMissing(context, DefaultHandlerExceptionResolver.class, source);
	}

	private static void registerIfMissing(ParserContext context, Class<?> componentType, Object source) {
		String beanName = componentType.getName();
		if (!context.getRegistry().containsBeanDefinition(beanName)) {
			RootBeanDefinition beanDefinition = new RootBeanDefinition(componentType);
			beanDefinition.setSource(source);
			beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			context.getRegistry().registerBeanDefinition(beanName, beanDefinition);
		}
	}
}
