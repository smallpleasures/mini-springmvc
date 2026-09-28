package com.yrainy.web.servlet.mvc.method.annotation;

import com.yrainy.web.servlet.ModelAndView;
import com.yrainy.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 解析控制器和 {@code @ControllerAdvice} 中声明的 {@code @ExceptionHandler} 方法。 */
public class ExceptionHandlerExceptionResolver extends AbstractHandlerExceptionResolver
		implements ApplicationContextAware, InitializingBean {

	private ApplicationContext applicationContext;
	private RequestMappingHandlerAdapter handlerAdapter;
	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache = new ConcurrentHashMap<>(64);
	private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> adviceCache = new LinkedHashMap<>();

	public ExceptionHandlerExceptionResolver() {
		setOrder(0);
		setPreventResponseCaching(true);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterPropertiesSet() {
		if (this.applicationContext == null) {
			throw new IllegalStateException("ApplicationContext 尚未设置");
		}
		initHandlerAdapter();
		initControllerAdviceCache();
	}

	/** 获取当前应用使用的处理器适配器，以复用其中配置的参数和返回值处理器。 */
	private void initHandlerAdapter() {
		Map<String, RequestMappingHandlerAdapter> adapters = BeanFactoryUtils.beansOfTypeIncludingAncestors(
				this.applicationContext, RequestMappingHandlerAdapter.class, true, false);
		if (!adapters.isEmpty()) {
			List<RequestMappingHandlerAdapter> candidates = new ArrayList<>(adapters.values());
			AnnotationAwareOrderComparator.sort(candidates);
			this.handlerAdapter = candidates.get(0);
			return;
		}
		if (!(this.applicationContext instanceof ConfigurableApplicationContext)) {
			throw new IllegalStateException("无法创建默认 RequestMappingHandlerAdapter");
		}
		ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) this.applicationContext;
		this.handlerAdapter = (RequestMappingHandlerAdapter) configurableContext.getAutowireCapableBeanFactory()
				.createBean(RequestMappingHandlerAdapter.class);
	}

	/** 缓存带有异常映射的全局通知，后续按声明顺序匹配异常。 */
	private void initControllerAdviceCache() {
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(this.applicationContext);
		AnnotationAwareOrderComparator.sort(adviceBeans);
		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("无法解析 ControllerAdviceBean 类型：" + adviceBean);
			}
			ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(beanType);
			if (resolver.hasExceptionMappings()) {
				this.adviceCache.put(adviceBean, resolver);
			}
		}
	}

	@Override
	protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
			Object handler, Exception exception) {
		HandlerMethod handlerMethod = (handler instanceof HandlerMethod ? (HandlerMethod) handler : null);
		Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);
		ServletInvocableHandlerMethod exceptionHandlerMethod =
				getExceptionHandlerMethod(handlerMethod, handlerType, exception);
		if (exceptionHandlerMethod == null) {
			return null;
		}

		try {
			configureExceptionHandlerMethod(exceptionHandlerMethod, handlerMethod);
			ServletWebRequest webRequest = new ServletWebRequest(request, response);
			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, getProvidedArgs(exception, handlerMethod));
			return getModelAndView(mavContainer);
		}
		catch (Exception invocationException) {
			if (logger.isWarnEnabled()) {
				logger.warn("调用 @ExceptionHandler 方法失败，继续尝试后续异常解析器", invocationException);
			}
			return null;
		}
	}

	/** 同一控制器中的异常方法优先；未匹配时再按顺序查找适用的全局通知。 */
	private ServletInvocableHandlerMethod getExceptionHandlerMethod(
			HandlerMethod handlerMethod, Class<?> handlerType, Exception exception) {
		if (handlerMethod != null && handlerType != null) {
			ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.computeIfAbsent(
					handlerType, ExceptionHandlerMethodResolver::new);
			Method method = resolver.resolveMethodByThrowable(exception);
			if (method != null) {
				return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method);
			}
		}

		for (Map.Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.adviceCache.entrySet()) {
			ControllerAdviceBean adviceBean = entry.getKey();
			if (handlerType != null && !adviceBean.isApplicableToBeanType(handlerType)) {
				continue;
			}
			Method method = entry.getValue().resolveMethodByThrowable(exception);
			if (method != null) {
				return new ServletInvocableHandlerMethod(adviceBean.resolveBean(), method);
			}
		}
		return null;
	}

	/** 为异常方法复用适配器中的解析器、返回值处理器、参数名发现器和数据绑定配置。 */
	private void configureExceptionHandlerMethod(ServletInvocableHandlerMethod invocableMethod,
			HandlerMethod originalHandlerMethod) throws Exception {
		List<HandlerMethodArgumentResolver> argumentResolvers = this.handlerAdapter.getArgumentResolvers();
		if (argumentResolvers != null) {
			invocableMethod.setHandlerMethodArgumentResolvers(
					new HandlerMethodArgumentResolverComposite().addResolvers(argumentResolvers));
		}

		List<HandlerMethodReturnValueHandler> returnValueHandlers = this.handlerAdapter.getReturnValueHandlers();
		if (returnValueHandlers != null) {
			HandlerMethodReturnValueHandlerComposite composite = new HandlerMethodReturnValueHandlerComposite();
			composite.addHandler(new ModelAndViewReturnValueHandler());
			composite.addHandlers(returnValueHandlers);
			invocableMethod.setHandlerMethodReturnValueHandlers(composite);
		}
		invocableMethod.setParameterNameDiscoverer(this.handlerAdapter.getParameterNameDiscoverer());
		if (originalHandlerMethod != null) {
			WebDataBinderFactory binderFactory = this.handlerAdapter.getDataBinderFactory(originalHandlerMethod);
			invocableMethod.setDataBinderFactory(binderFactory);
		}
	}

	/** 向异常方法直接提供异常及其原因链和原始处理器方法。 */
	private Object[] getProvidedArgs(Exception exception, HandlerMethod handlerMethod) {
		List<Object> providedArgs = new ArrayList<>();
		Throwable current = exception;
		while (current != null) {
			providedArgs.add(current);
			Throwable cause = current.getCause();
			if (cause == current) {
				break;
			}
			current = cause;
		}
		if (handlerMethod != null) {
			providedArgs.add(handlerMethod);
		}
		return providedArgs.toArray();
	}

	/** 将方法返回值转换为分发器可处理的视图模型或已完成响应标记。 */
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer) {
		if (mavContainer.isRequestHandled()) {
			return new ModelAndView();
		}
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), mavContainer.getModel(), mavContainer.getStatus());
		if (!mavContainer.isViewReference()) {
			mav.setView((com.yrainy.web.servlet.View) mavContainer.getView());
		}
		return mav;
	}

	/** 支持异常方法直接返回框架自有的 ModelAndView 类型。 */
	private static class ModelAndViewReturnValueHandler implements HandlerMethodReturnValueHandler {

		@Override
		public boolean supportsReturnType(MethodParameter returnType) {
			return ModelAndView.class.isAssignableFrom(returnType.getParameterType());
		}

		@Override
		public void handleReturnValue(Object returnValue, MethodParameter returnType,
				ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
			if (returnValue == null) {
				mavContainer.setRequestHandled(true);
				return;
			}
			ModelAndView mav = (ModelAndView) returnValue;
			if (mav.wasCleared()) {
				mavContainer.setRequestHandled(true);
				return;
			}
			if (mav.isReference()) {
				mavContainer.setViewName(mav.getViewName());
			}
			else if (mav.hasView()) {
				mavContainer.setView(mav.getView());
			}
			mavContainer.addAllAttributes(mav.getModel());
			if (mav.getStatus() != null) {
				mavContainer.setStatus(mav.getStatus());
				HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
				if (response != null) {
					response.setStatus(mav.getStatus().value());
				}
			}
			if (!mav.hasView() && mav.getModel().isEmpty()) {
				mavContainer.setRequestHandled(true);
			}
		}
	}
}
