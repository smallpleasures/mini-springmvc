package com.yrainy.web.servlet.config;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class MvcNamespaceHandler extends NamespaceHandlerSupport {

	@Override
	public void init() {
		registerBeanDefinitionParser("annotation-driven", new AnnotationDrivenBeanDefinitionParser());
	}

}
