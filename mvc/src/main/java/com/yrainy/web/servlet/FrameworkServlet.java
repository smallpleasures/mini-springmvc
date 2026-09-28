package com.yrainy.web.servlet;

import org.springframework.beans.BeanUtils;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** 初始化 Web 应用上下文，并将 Servlet 生命周期与 Spring 上下文生命周期衔接起来。 */
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {

    /** Web 应用上下文命名空间后缀。 */
    public static final String DEFAULT_NAMESPACE_SUFFIX = "-servlet";

    /** 默认使用的 XML Web 应用上下文实现。 */
    public static final Class<?> DEFAULT_CONTEXT_CLASS = XmlWebApplicationContext.class;

    /** Web 应用上下文在 ServletContext 中的属性名前缀，后接 Servlet 名称。 */
    public static final String SERVLET_CONTEXT_PREFIX = FrameworkServlet.class.getName() + ".CONTEXT.";

    /** 用于分隔 init-param 中多个配置值的字符集合。 */
    private static final String INIT_PARAM_DELIMITERS = ",; \t\n";


    /** 用于从 ServletContext 中获取 Web 应用上下文的属性名。 */
    private String contextAttribute;

    /** 待创建的 Web 应用上下文实现类型。 */
    private Class<?> contextClass = DEFAULT_CONTEXT_CLASS;

    /** 分配给 Web 应用上下文的标识。 */
    private String contextId;

    /** 当前 Servlet 的配置命名空间。 */
    private String namespace;

    /** 显式指定的上下文配置文件位置。 */
    private String contextConfigLocation;

    /** 应用于上下文的初始化器实例列表。 */
    private final List<ApplicationContextInitializer<ConfigurableApplicationContext>> contextInitializers =
            new ArrayList<>();

    /** 通过 init-param 指定的初始化器类名，多个类名以逗号分隔。 */
    private String contextInitializerClasses;

    /** 是否将上下文发布为 ServletContext 属性。 */
    private boolean publishContext = true;

    /** 每个请求结束时是否发布 ServletRequestHandledEvent 事件。 */
    private boolean publishEvents = true;

    /** 是否允许子线程继承 LocaleContext 和 RequestAttributes。 */
    private boolean threadContextInheritable = false;

    /** 是否将 HTTP OPTIONS 请求分派到 {@link #doService}。 */
    private boolean dispatchOptionsRequest = false;

    /** 是否将 HTTP TRACE 请求分派到 {@link #doService}。 */
    private boolean dispatchTraceRequest = false;

    /** 是否记录可能包含敏感信息的请求参数和请求头。 */
    private boolean enableLoggingRequestDetails = false;

    /** 当前 Servlet 使用的 Web 应用上下文。 */
    private WebApplicationContext webApplicationContext;

    /** 标记 Web 应用上下文是否通过 {@link #setApplicationContext} 注入。 */
    private boolean webApplicationContextInjected = false;

    /** 标记上下文刷新回调是否已执行。 */
    private volatile boolean refreshEventReceived;

    /** 用于同步执行 onRefresh 回调的监视器。 */
    private final Object onRefreshMonitor = new Object();

    public FrameworkServlet(WebApplicationContext webApplicationContext) {
        this.webApplicationContext = webApplicationContext;
    }

    public String getNamespace() {
        return (this.namespace != null ? this.namespace : getServletName() + DEFAULT_NAMESPACE_SUFFIX);
    }

    public String getServletContextAttributeName() {
        return SERVLET_CONTEXT_PREFIX + getServletName();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        if (this.webApplicationContext == null && applicationContext instanceof WebApplicationContext) {
            this.webApplicationContext = (WebApplicationContext) applicationContext;
            this.webApplicationContextInjected = true;
        }
    }

    public final WebApplicationContext getWebApplicationContext() {
        return this.webApplicationContext;
    }

    public String getContextAttribute() {
        return this.contextAttribute;
    }

    public Class<?> getContextClass() {
        return this.contextClass;
    }

    public String getContextConfigLocation() {
        return this.contextConfigLocation;
    }

    /** 设置当前 Servlet 加载的 Spring Web 应用上下文配置位置。 */
    public void setContextConfigLocation(String contextConfigLocation) {
        this.contextConfigLocation = contextConfigLocation;
    }

    @Override
    protected final void initServletBean() throws ServletException {
        getServletContext().log("Initializing Spring " + getClass().getSimpleName() + " '" + getServletName() + "'");
        if (logger.isInfoEnabled()) {
            logger.info("Initializing Servlet '" + getServletName() + "'");
        }
        long startTime = System.currentTimeMillis();

        try {
            this.webApplicationContext = initWebApplicationContext();
            initFrameworkServlet();
        }
        catch (ServletException | RuntimeException ex) {
            logger.error("Context initialization failed", ex);
            throw ex;
        }

        if (logger.isDebugEnabled()) {
            String value = this.enableLoggingRequestDetails ?
                    "shown which may lead to unsafe logging of potentially sensitive data" :
                    "masked to prevent unsafe logging of potentially sensitive data";
            logger.debug("enableLoggingRequestDetails='" + this.enableLoggingRequestDetails +
                    "': request parameters and headers will be " + value);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Completed initialization in " + (System.currentTimeMillis() - startTime) + " ms");
        }
    }

    /** Servlet 容器和 Spring Web 上下文初始化完成后的扩展钩子。 */
    protected void initFrameworkServlet() throws ServletException {
    }

    protected WebApplicationContext initWebApplicationContext() {
        // 获取 ContextLoaderListener 创建的父容器。
        WebApplicationContext rootContext =
                WebApplicationContextUtils.getWebApplicationContext(getServletContext());
        WebApplicationContext wac = null;

        if (this.webApplicationContext != null) {
            // 获得子容器
            wac = this.webApplicationContext;
            if (wac instanceof ConfigurableWebApplicationContext) {
                ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) wac;
                if (!cwac.isActive()) {
                    // 未设置父容器时，使用根容器作为父容器。
                    if (cwac.getParent() == null) {
                        cwac.setParent(rootContext);
                    }
                    // 配置并且加载子容器
                    configureAndRefreshWebApplicationContext(cwac);
                }
            }
        }
        if (wac == null) {
            // 根据配置的属性名从 ServletContext 中获取 Web 应用上下文。
            wac = findWebApplicationContext();
        }
        if (wac == null) {
            // 未提供上下文实例时，根据配置创建 XML Web 应用上下文。
            wac = createWebApplicationContext(rootContext);
        }

        // 上下文刷新事件尚未触发时，主动初始化分发器策略。
        if (!this.refreshEventReceived) {
            synchronized (this.onRefreshMonitor) {
                onRefresh(wac);
            }
        }

        if (this.publishContext) {
            // 将当前上下文保存到 ServletContext，供其他组件获取。
            String attrName = getServletContextAttributeName();
            getServletContext().setAttribute(attrName, wac);
        }

        return wac;
    }

    protected WebApplicationContext findWebApplicationContext() {
        String attrName = getContextAttribute();
        if (attrName == null) {
            return null;
        }
        WebApplicationContext wac =
                WebApplicationContextUtils.getWebApplicationContext(getServletContext(), attrName);
        if (wac == null) {
            throw new IllegalStateException("No WebApplicationContext found: initializer not registered?");
        }
        return wac;
    }

    protected WebApplicationContext createWebApplicationContext(ApplicationContext parent) {
        Class<?> contextClass = getContextClass();
        if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
            throw new ApplicationContextException(
                    "Fatal initialization error in servlet with name '" + getServletName() +
                            "': custom WebApplicationContext class [" + contextClass.getName() +
                            "] is not of type ConfigurableWebApplicationContext");
        }
        ConfigurableWebApplicationContext wac =
                (ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);

        wac.setEnvironment(getEnvironment());
        wac.setParent(parent);
        String configLocation = getContextConfigLocation();
        if (configLocation != null) {
            wac.setConfigLocation(configLocation);
        }
        configureAndRefreshWebApplicationContext(wac);

        return wac;
    }

    protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac) {
        if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
            // 设置id
            if (this.contextId != null) {
                wac.setId(this.contextId);
            }
            else {
                // 生成默认上下文标识。
                wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
                        ObjectUtils.getDisplayString(getServletContext().getContextPath()) + '/' + getServletName());
            }
        }
        // 设置 Servlet 上下文。
        wac.setServletContext(getServletContext());
        wac.setServletConfig(getServletConfig());
        wac.setNamespace(getNamespace());
        // 监听器  委托设计模式
        wac.addApplicationListener(new SourceFilteringListener(wac, new ContextRefreshListener()));

        // 将 init-param 配置写入 Environment。
        ConfigurableEnvironment env = wac.getEnvironment();
        if (env instanceof ConfigurableWebEnvironment) {
            ((ConfigurableWebEnvironment) env).initPropertySources(getServletContext(), getServletConfig());
        }
        postProcessWebApplicationContext(wac);
        // 容器启动前初始化
        applyInitializers(wac);
        wac.refresh();
    }

    /** 在 Web 上下文刷新前允许子类补充配置。 */
    protected void postProcessWebApplicationContext(ConfigurableWebApplicationContext wac) {
    }

    protected void applyInitializers(ConfigurableApplicationContext wac) {
        String globalClassNames = getServletContext().getInitParameter(ContextLoader.GLOBAL_INITIALIZER_CLASSES_PARAM);
        if (globalClassNames != null) {
            for (String className : StringUtils.tokenizeToStringArray(globalClassNames, INIT_PARAM_DELIMITERS)) {
                this.contextInitializers.add(loadInitializer(className, wac));
            }
        }

        if (this.contextInitializerClasses != null) {
            for (String className : StringUtils.tokenizeToStringArray(this.contextInitializerClasses, INIT_PARAM_DELIMITERS)) {
                this.contextInitializers.add(loadInitializer(className, wac));
            }
        }

        AnnotationAwareOrderComparator.sort(this.contextInitializers);
        for (ApplicationContextInitializer<ConfigurableApplicationContext> initializer : this.contextInitializers) {
            initializer.initialize(wac);
        }
    }

    private ApplicationContextInitializer<ConfigurableApplicationContext> loadInitializer(
            String className, ConfigurableApplicationContext wac) {
        try {
            Class<?> initializerClass = ClassUtils.forName(className, wac.getClassLoader());
            Class<?> initializerContextClass =
                    GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
            if (initializerContextClass != null && !initializerContextClass.isInstance(wac)) {
                throw new ApplicationContextException(String.format(
                        "Could not apply context initializer [%s] since its generic parameter [%s] " +
                                "is not assignable from the type of application context used by this " +
                                "framework servlet: [%s]", initializerClass.getName(), initializerContextClass.getName(),
                        wac.getClass().getName()));
            }
            return BeanUtils.instantiateClass(initializerClass, ApplicationContextInitializer.class);
        }
        catch (ClassNotFoundException ex) {
            throw new ApplicationContextException(String.format("Could not load class [%s] specified " +
                    "via 'contextInitializerClasses' init-param", className), ex);
        }
    }

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    /**
     * 将 POST 请求交给 {@link #processRequest} 统一处理。
     * @see #doService
     */
    @Override
    protected final void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    /**
     * 将 PUT 请求交给 {@link #processRequest} 统一处理。
     * @see #doService
     */
    @Override
    protected final void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    /**
     * 将 DELETE 请求交给 {@link #processRequest} 统一处理。
     * @see #doService
     */
    @Override
    protected final void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    protected final void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        Throwable failureCause = null;

        LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
        LocaleContext localeContext = buildLocaleContext(request);

        RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes requestAttributes = buildRequestAttributes(request, response, previousAttributes);

        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        asyncManager.registerCallableInterceptor(FrameworkServlet.class.getName(), new RequestBindingInterceptor());

        initContextHolders(request, localeContext, requestAttributes);

        try {
            doService(request, response);
        }
        catch (ServletException | IOException ex) {
            failureCause = ex;
            throw ex;
        }
        catch (Throwable ex) {
            failureCause = ex;
            throw new NestedServletException("Request processing failed", ex);
        }

        finally {
            resetContextHolders(request, previousLocaleContext, previousAttributes);
            if (requestAttributes != null) {
                requestAttributes.requestCompleted();
            }
            publishRequestHandledEvent(request, response, startTime, failureCause);
        }
    }

    public void onApplicationEvent(ContextRefreshedEvent event) {
        this.refreshEventReceived = true;
        synchronized (this.onRefreshMonitor) {
            onRefresh(event.getApplicationContext());
        }
    }

    protected abstract void onRefresh(ApplicationContext context);

    public void refresh() {
        WebApplicationContext wac = getWebApplicationContext();
        if (!(wac instanceof ConfigurableApplicationContext)) {
            throw new IllegalStateException("WebApplicationContext does not support refresh: " + wac);
        }
        ((ConfigurableApplicationContext) wac).refresh();
    }

    protected abstract void doService(HttpServletRequest request, HttpServletResponse response) throws Exception;;

    private class ContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            FrameworkServlet.this.onApplicationEvent(event);
        }
    }

    protected ServletRequestAttributes buildRequestAttributes(HttpServletRequest request,
                                                              HttpServletResponse response, RequestAttributes previousAttributes) {

        if (previousAttributes == null || previousAttributes instanceof ServletRequestAttributes) {
            return new ServletRequestAttributes(request, response);
        }
        else {
            return null;  // preserve the pre-bound RequestAttributes instance
        }
    }

    protected LocaleContext buildLocaleContext(HttpServletRequest request) {
        return new SimpleLocaleContext(request.getLocale());
    }

    private void publishRequestHandledEvent(HttpServletRequest request, HttpServletResponse response,
                                            long startTime, Throwable failureCause) {

        if (this.publishEvents && this.webApplicationContext != null) {
        // 无论请求处理成功与否，都发布请求处理完成事件。
            long processingTime = System.currentTimeMillis() - startTime;
            this.webApplicationContext.publishEvent(
                    new ServletRequestHandledEvent(this,
                            request.getRequestURI(), request.getRemoteAddr(),
                            request.getMethod(), getServletConfig().getServletName(),
                            WebUtils.getSessionId(request), getUsernameForRequest(request),
                            processingTime, failureCause, response.getStatus()));
        }
    }

    protected String getUsernameForRequest(HttpServletRequest request) {
        Principal userPrincipal = request.getUserPrincipal();
        return (userPrincipal != null ? userPrincipal.getName() : null);
    }

    private void initContextHolders(HttpServletRequest request,
                                    LocaleContext localeContext, RequestAttributes requestAttributes) {

        if (localeContext != null) {
            LocaleContextHolder.setLocaleContext(localeContext, this.threadContextInheritable);
        }
        if (requestAttributes != null) {
            RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
        }
    }

    private void resetContextHolders(HttpServletRequest request,
                                     LocaleContext prevLocaleContext, RequestAttributes previousAttributes) {

        LocaleContextHolder.setLocaleContext(prevLocaleContext, this.threadContextInheritable);
        RequestContextHolder.setRequestAttributes(previousAttributes, this.threadContextInheritable);
    }

    private class RequestBindingInterceptor implements CallableProcessingInterceptor {

        @Override
        public <T> void preProcess(NativeWebRequest webRequest, Callable<T> task) {
            HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
            if (request != null) {
                HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
                initContextHolders(request, buildLocaleContext(request),
                        buildRequestAttributes(request, response, null));
            }
        }
        @Override
        public <T> void postProcess(NativeWebRequest webRequest, Callable<T> task, Object concurrentResult) {
            HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
            if (request != null) {
                resetContextHolders(request, null, null);
            }
        }
    }
}
