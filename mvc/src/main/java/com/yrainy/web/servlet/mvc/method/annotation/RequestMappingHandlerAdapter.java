
package com.yrainy.web.servlet.mvc.method.annotation;

import com.yrainy.web.servlet.ModelAndView;
import com.yrainy.web.servlet.View;
import com.yrainy.web.servlet.mvc.annotation.ModelAndViewResolver;
import com.yrainy.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import com.yrainy.web.servlet.support.RequestContextUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.*;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.*;
import org.springframework.web.method.support.*;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AbstractHandlerMethodAdapter} 的扩展实现，用于调用带有
 * {@link RequestMapping @RequestMapping} 注解的 {@link HandlerMethod}。
 *
 * <p>可通过 {@link #setCustomArgumentResolvers} 和 {@link #setCustomReturnValueHandlers}
 * 添加自定义参数解析器与返回值处理器；也可通过 {@link #setArgumentResolvers} 和
 * {@link #setReturnValueHandlers} 完整替换默认配置。
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	/**
	 * 由 {@code spring.xml.ignore} 系统属性控制的布尔标记；启用后不初始化 XML 相关基础设施。
	 * <p>默认值为 {@code false}。
	 */
	private static final boolean shouldIgnoreXml = SpringProperties.getFlag("spring.xml.ignore");

	/**
	 * 用于匹配 {@link InitBinder @InitBinder} 方法的方法过滤器。
	 */
	public static final MethodFilter INIT_BINDER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, InitBinder.class);

	/**
	 * 用于匹配 {@link ModelAttribute @ModelAttribute} 方法的方法过滤器。
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
			(!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class) &&
					AnnotatedElementUtils.hasAnnotation(method, ModelAttribute.class));
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;
	private HandlerMethodArgumentResolverComposite argumentResolvers;
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
	private List<ModelAndViewResolver> modelAndViewResolvers;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	private List<HttpMessageConverter<?>> messageConverters;

	private final List<Object> requestResponseBodyAdvice = new ArrayList<>();
	private WebBindingInitializer webBindingInitializer;

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");
	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	private boolean ignoreDefaultModelOnRedirect = false;

	private int cacheSecondsForSessionAttributeHandlers = 0;

	private boolean synchronizeOnSession = false;

	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
	private ConfigurableBeanFactory beanFactory;

	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);

	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();


	public RequestMappingHandlerAdapter() {
		this.messageConverters = new ArrayList<>(4);
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(new StringHttpMessageConverter());
		// 默认转换器同时支持 JSON 控制器的请求体读取和响应体写入。
		this.messageConverters.add(new MappingJackson2HttpMessageConverter());
		if (!shouldIgnoreXml) {
			try {
				this.messageConverters.add(new SourceHttpMessageConverter<>());
			}
			catch (Error err) {
				// 当前环境没有 TransformerFactory 实现时，跳过 XML 转换器。
			}
		}
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * 设置自定义参数类型的解析器。自定义解析器排在内置解析器之后；如需替换内置解析器，
	 * 请改用 {@link #setArgumentResolvers}。
	 */
	public void setCustomArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * 返回自定义参数解析器；未配置时返回 {@code null}。
	 */
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * 设置支持的全部参数类型，并替换原本会自动配置的默认解析器。
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * 返回已配置的参数解析器；尚未执行 {@link #afterPropertiesSet()} 时可能为 {@code null}。
	 */
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * 设置 {@code @InitBinder} 方法支持的参数类型。
	 */
	public void setInitBinderArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * 返回 {@code @InitBinder} 方法使用的参数解析器；尚未执行 {@link #afterPropertiesSet()} 时可能为 {@code null}。
	 */
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
	}

	/**
	 * 设置自定义返回值类型的处理器。自定义处理器排在内置处理器之后；如需替换内置处理器，
	 * 请改用 {@link #setReturnValueHandlers}。
	 */
	public void setCustomReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * 返回自定义返回值处理器；未配置时返回 {@code null}。
	 */
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * 设置支持的全部返回值类型，并替换原本会自动配置的默认处理器。
	 */
	public void setReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * 返回已配置的返回值处理器；尚未执行 {@link #afterPropertiesSet()} 时可能为 {@code null}。
	 */
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * 设置自定义 {@link ModelAndViewResolver}。
	 * <p><strong>注意：</strong>此方法仅为保持向后兼容而保留，建议改用
	 * {@link HandlerMethodReturnValueHandler}。由于无法为旧接口实现
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType}，两者无法直接适配；
	 * 因此旧解析器只能在其他返回值处理器之后调用。新处理器可以访问返回值类型和控制器方法，
	 * 并可灵活调整执行顺序。
	 */
	public void setModelAndViewResolvers(List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * 返回已配置的 {@link ModelAndViewResolver} 列表；未配置时返回 {@code null}。
	 */
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * 设置用于确定请求媒体类型的 {@link ContentNegotiationManager}。
	 * 未设置时使用默认实例。
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * 设置参数解析器和返回值处理器使用的消息转换器，用于读取请求体或写入响应体。
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * 返回已配置的消息体转换器。
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * 添加 {@code RequestBodyAdvice}，在读取并转换 {@code @RequestBody} 或
	 * {@code HttpEntity} 方法参数之前拦截请求。
	 */
	public void setRequestBodyAdvice(List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * 添加 {@code ResponseBodyAdvice}，在将 {@code @ResponseBody} 或
	 * {@code ResponseEntity} 返回值写入响应体之前拦截响应。
	 */
	public void setResponseBodyAdvice(List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * 设置应用于每个 DataBinder 实例的全局初始化器。
	 */
	public void setWebBindingInitializer(WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * 返回已配置的 WebBindingInitializer；未配置时返回 {@code null}。
	 */
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * 设置控制器方法返回 {@link Callable} 时使用的默认 {@link AsyncTaskExecutor}。
	 * 控制器也可以通过返回 {@link WebAsyncTask} 为单个请求指定执行器。
	 * <p>默认使用 {@link SimpleAsyncTaskExecutor}。该执行器不会复用线程，生产环境建议替换。
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * 设置并发请求的超时时间，单位为毫秒。在 Servlet 3 中，计时从主请求处理线程退出后开始，
	 * 到请求重新分派以处理并发结果时结束。
	 * <p>未设置时使用底层实现的默认超时时间。
	 * @param timeout 超时时间，单位为毫秒
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * 设置注册到异步请求中的 {@code CallableProcessingInterceptor}。
	 * @param interceptors 要注册的拦截器
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * 设置注册到异步请求中的 {@code DeferredResultProcessingInterceptor}。
	 * @param interceptors 要注册的拦截器
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * 设置用于适配控制器响应式返回值类型的注册表。
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * 返回已配置的响应式类型适配器注册表。
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * 默认模型通常同时用于视图渲染和重定向。控制器方法也可以声明
	 * {@link RedirectAttributes} 参数，为重定向单独提供属性。
	 * <p>设为 {@code true} 后，即使方法未声明该参数，重定向也不会使用默认模型；
	 * 设为 {@code false} 时，未声明该参数的重定向仍可能使用默认模型。
	 * <p>默认值为 {@code false}，新应用可考虑启用此选项。
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * 设置会话属性的存储策略。默认使用 {@link DefaultSessionAttributeStore}，
	 * 并在 HttpSession 中沿用模型属性的名称保存属性。
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * 设置 {@code @SessionAttributes} 处理器响应内容的缓存时间，单位为秒。
	 * <p>可选值如下：
	 * <ul>
	 * <li>-1：不生成缓存相关响应头</li>
	 * <li>0（默认值）：通过 {@code Cache-Control: no-store} 禁止缓存</li>
	 * <li>大于等于 1：通过 {@code Cache-Control: max-age=秒数} 请求缓存；会话属性通常不建议缓存</li>
	 * </ul>
	 * <p>{@code cacheSeconds} 属性适用于普通处理器，而本设置仅适用于声明
	 * {@code @SessionAttributes} 的处理器。
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * 设置控制器是否按会话串行执行，以便顺序处理来自同一客户端的并发调用。
	 * <p>启用后将使用会话互斥对象加锁。优先使用 HttpSessionMutexListener 提供的互斥对象；
	 * 在多数容器中也可直接使用 HttpSession，但不同容器对其对象引用的稳定性没有统一保证。
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see WebUtils#getSessionMutex(HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * 设置方法参数名发现器，用于需要读取参数名称的场景，例如推导默认模型属性名。
	 * <p>默认使用 {@link DefaultParameterNameDiscoverer}。
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/** 返回当前适配器使用的参数名发现器，供异常处理方法调用流程复用。 */
	ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * 解析方法参数默认值中的表达式时需要 {@link ConfigurableBeanFactory}。
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * 返回此 Bean 所属的工厂；未设置时返回 {@code null}。
	 */
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		// 先处理全局控制器通知 Bean，因为其中可能包含响应体通知组件。
		initControllerAdviceCache();
		// 参数解析器
		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}//数据绑定器参数解析器
		if (this.initBinderArgumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}// 返回值处理器
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}

		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
			}
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
			}
			if (RequestBodyAdvice.class.isAssignableFrom(beanType) || ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
			}
		}

		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}

		if (logger.isDebugEnabled()) {
			int modelSize = this.modelAttributeAdviceCache.size();
			int binderSize = this.initBinderAdviceCache.size();
			int reqCount = getBodyAdviceCount(RequestBodyAdvice.class);
			int resCount = getBodyAdviceCount(ResponseBodyAdvice.class);
			if (modelSize == 0 && binderSize == 0 && reqCount == 0 && resCount == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " + modelSize + " @ModelAttribute, " + binderSize +
						" @InitBinder, " + reqCount + " RequestBodyAdvice, " + resCount + " ResponseBodyAdvice");
			}
		}
	}

	// 统计所有通知组件，包括显式注册的组件。

	private int getBodyAdviceCount(Class<?> adviceType) {
		List<Object> advice = this.requestResponseBodyAdvice;
		return RequestBodyAdvice.class.isAssignableFrom(adviceType) ?
				RequestResponseBodyAdviceChain.getAdviceByType(advice, RequestBodyAdvice.class).size() :
				RequestResponseBodyAdviceChain.getAdviceByType(advice, ResponseBodyAdvice.class).size();
	}

	/**
	 * 返回要使用的全部参数解析器，包括内置解析器和通过
	 * {@link #setCustomArgumentResolvers} 提供的自定义解析器。
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(30);

		// 根据注解解析参数。
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new ModelAttributeMethodProcessor(false));
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));

		// 根据参数类型解析参数。
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());
		resolvers.add(new MapMethodProcessor());
		resolvers.add(new ErrorsMethodArgumentResolver());
		resolvers.add(new SessionStatusMethodArgumentResolver());

		// 添加自定义参数解析器。
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// 兜底解析器。
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * 返回 {@code @InitBinder} 方法使用的全部参数解析器，包括内置解析器和自定义解析器。
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(20);

		// 根据注解解析参数。
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));

		// 根据参数类型解析参数。
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// 添加自定义参数解析器。
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// 兜底解析器。
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * 返回要使用的全部返回值处理器，包括内置处理器和通过
	 * {@link #setReturnValueHandlers} 提供的自定义处理器。
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(20);

		// 仅处理单一类型的返回值。
		handlers.add(new ModelMethodProcessor());

		// 根据注解处理返回值。
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		// 处理多种类型的返回值。
		handlers.add(new MapMethodProcessor());

		// 添加自定义返回值处理器。
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// 兜底处理器。
		handlers.add(new ModelAttributeMethodProcessor(true));

		return handlers;
	}


	/**
	 * 始终返回 {@code true}，因为每种方法参数和返回值都会由某种方式处理。
	 * 未被 HandlerMethodArgumentResolver 识别的参数：简单类型按请求参数解析，其他类型按模型属性解析；
	 * 未被 HandlerMethodReturnValueHandler 识别的返回值按模型属性处理。
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request,
										  HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		// 检查请求方法是否受支持，以及当前请求是否必须携带会话。
		checkRequest(request);

		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				synchronized (WebUtils.getSessionMutex(session)) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}


		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);
			}
		}

		return mav;
	}

	/**
		 * 此实现始终返回 -1。{@code @RequestMapping} 方法可以自行计算最后修改时间，调用
		 * {@link WebRequest#checkNotModified(long)}，并在结果为 {@code true} 时返回 {@code null}。
	 */
	@Override
	@SuppressWarnings("deprecation")
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * 返回给定处理器类型对应的 {@link SessionAttributesHandler} 实例，不会返回 {@code null}。
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		return this.sessionAttributesHandlerCache.computeIfAbsent(
				handlerMethod.getBeanType(),
				type -> new SessionAttributesHandler(type, this.sessionAttributeStore));
	}

	/**
	 * 调用 {@link RequestMapping} 处理器方法；需要解析视图时构造 {@link ModelAndView}。
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
		// 将请求和响应封装为 ServletWebRequest。
		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			// 获取全局配置以及当前控制器中的 InitBinder 方法，用于绑定参数。
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);

			// 获取全局配置以及当前控制器中的 ModelAttribute 方法，这些方法会在目标方法调用前执行。
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

			// 封装处理器方法，以便在调用前解析参数、调用后处理返回值。
			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
			if (this.argumentResolvers != null) {
				// 为可调用方法设置参数解析器。
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
			}
			if (this.returnValueHandlers != null) {
				// 为可调用方法设置返回值处理器。
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
			}
			// 为可调用方法设置数据绑定工厂。
			invocableMethod.setDataBinderFactory(binderFactory);
			// 设置参数名发现器，以便获取当前方法的参数名称。
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
			// 创建视图模型容器。
			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			// 将请求中的 Flash 属性复制到模型。
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
			// 调用带有 @ModelAttribute 注解的方法，为目标处理器方法准备模型数据。
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);
			// 设置重定向时是否忽略默认模型；默认值为 false。
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

			// 创建 AsyncWebRequest，用于管理控制器返回 WebAsyncTask、DeferredResult 等异步结果的处理。
			// 异步逻辑完成后，容器会重新分派请求并将结果写入响应，从而及时释放当前请求线程。
			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);
			// 将执行器、异步请求和拦截器注册到 WebAsyncManager。
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

			// 若请求已产生并发结果，则恢复结果及对应的模型容器。
			if (asyncManager.hasConcurrentResult()) {
				Object result = asyncManager.getConcurrentResult();
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				LogFormatUtils.traceDebug(logger, traceOn -> {
					String formatted = LogFormatUtils.formatValue(result, !traceOn);
					return "Resume with async result [" + formatted + "]";
				});
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}
			// 解析请求参数并调用目标处理器方法，然后处理其返回值。
			invocableMethod.invokeAndHandle(webRequest, mavContainer);
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}

				// 更新模型，并根据处理结果生成视图模型；重定向时同时处理 Flash 属性。
			return getModelAndView(mavContainer, modelFactory, webRequest);
		}
		finally {
			webRequest.requestCompleted();
		}
	}

	/**
	 * 根据给定的 {@link HandlerMethod} 创建 {@link ServletInvocableHandlerMethod}。
	 * @param handlerMethod 处理器方法定义
	 * @return 对应的 ServletInvocableHandlerMethod 实例，也可以是其自定义子类
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
			this.modelAttributeCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
		// 先添加全局控制器通知方法。
		this.modelAttributeAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.initBinderCache.get(handlerType);
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();
		// 先添加全局控制器通知方法。
		this.initBinderAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
		return createDataBinderFactory(initBinderMethods);
	}

	/** 根据控制器返回值构造视图模型；已直接写入响应时返回 null。 */
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
			ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {

		modelFactory.updateModel(webRequest, mavContainer);
		if (mavContainer.isRequestHandled()) {
			return null;
		}

		ModelMap model = mavContainer.getModel();
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}
		return mav;
	}

	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		if (this.initBinderArgumentResolvers != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * 创建新的 InitBinderDataBinderFactory 实例的模板方法。
	 * <p>默认实现创建 ServletRequestDataBinderFactory；需要自定义 ServletRequestDataBinder 子类时可覆盖此方法。
	 * @param binderMethods {@code @InitBinder} 方法列表
	 * @return 要使用的 InitBinderDataBinderFactory 实例
	 * @throws Exception 状态无效或参数不正确时抛出
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

}
