
package com.yrainy.web.servlet.mvc.method.annotation;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.support.InvocableHandlerMethod;

import java.util.List;

/**
 * 创建 {@code ServletRequestDataBinder} 的工厂。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ServletRequestDataBinderFactory extends InitBinderDataBinderFactory {

	/**
	 * 创建工厂实例。
	 * @param binderMethods 一个或多个 {@code @InitBinder} 方法
	 * @param initializer 全局数据绑定初始化器
	 */
	public ServletRequestDataBinderFactory(List<InvocableHandlerMethod> binderMethods,
                                           WebBindingInitializer initializer) {

		super(binderMethods, initializer);
	}

	/**
	 * 返回 {@link ExtendedServletRequestDataBinder} 实例。
	 */
	@Override
	protected ServletRequestDataBinder createBinderInstance(
			Object target, String objectName, NativeWebRequest request) throws Exception  {

		return new ExtendedServletRequestDataBinder(target, objectName);
	}

}
