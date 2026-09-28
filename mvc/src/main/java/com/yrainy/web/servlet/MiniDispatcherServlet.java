package com.yrainy.web.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/** 负责接收 Servlet 请求并协调处理器映射、调用、异常处理和响应渲染。 */
public class MiniDispatcherServlet extends FrameworkServlet {

    /** MultipartResolver 组件在容器中的约定名称。 */
    public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

    /** LocaleResolver 组件在容器中的约定名称。 */
    public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

    /** ThemeResolver 组件在容器中的约定名称。 */
    public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

    /** 关闭全部映射器自动发现时使用的 HandlerMapping Bean 名称。 */
    public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

    /** 关闭全部适配器自动发现时使用的 HandlerAdapter Bean 名称。 */
    public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

    /** 关闭全部异常解析器自动发现时使用的 HandlerExceptionResolver Bean 名称。 */
    public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

    /** RequestToViewNameTranslator 组件在容器中的约定名称。 */
    public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

    /** 关闭全部视图解析器自动发现时使用的 ViewResolver Bean 名称。 */
    public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

    /** FlashMapManager 组件在容器中的约定名称。 */
    public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

    /** 保存当前 Web 应用上下文的请求属性名。 */
    public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".CONTEXT";

    /** 保存当前地区解析器的请求属性名。 */
    public static final String LOCALE_RESOLVER_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

    /** 保存当前主题解析器的请求属性名。 */
    public static final String THEME_RESOLVER_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".THEME_RESOLVER";

    /** 保存当前主题资源的请求属性名。 */
    public static final String THEME_SOURCE_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".THEME_SOURCE";

    /** 保存上一次请求传入的只读 Flash 属性映射的请求属性名。 */
    public static final String INPUT_FLASH_MAP_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

    /** 保存待传递到后续请求的 Flash 属性映射的请求属性名。 */
    public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

    /** 保存 FlashMapManager 实例的请求属性名。 */
    public static final String FLASH_MAP_MANAGER_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

    /** 保存已由异常解析器处理、但未渲染视图的异常的请求属性名。 */
    public static final String EXCEPTION_ATTRIBUTE = MiniDispatcherServlet.class.getName() + ".EXCEPTION";

    /** 未找到请求处理器时使用的日志分类。 */
    public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

    /** 定义分发器默认策略类名的类路径资源。 */
    private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

    /** 默认策略 Bean 属性使用的公共前缀。 */
    private static final String DEFAULT_STRATEGIES_PREFIX = "com.yrainy.web.servlet";

    /** 未找到请求处理器时使用的附加日志记录器。 */
    protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

    /** 缓存默认策略配置。 */
    private static Properties defaultStrategies;

    /** 是否自动发现容器中的全部 HandlerMapping。 */
    private boolean detectAllHandlerMappings = true;

    /** 是否自动发现容器中的全部 HandlerAdapter。 */
    private boolean detectAllHandlerAdapters = true;

    /** 是否自动发现容器中的全部 HandlerExceptionResolver。 */
    private boolean detectAllHandlerExceptionResolvers = true;

    /** 是否自动发现容器中的全部 ViewResolver。 */
    private boolean detectAllViewResolvers = true;

    /** 未找到处理器时是否抛出 NoHandlerFoundException。 */
    private boolean throwExceptionIfNoHandlerFound = false;

    /** Include 请求结束后是否清理请求属性。 */
    private boolean cleanupAfterInclude = true;

    /** 当前分发器使用的文件上传解析器。 */
    private MultipartResolver multipartResolver;

    /** 当前分发器使用的地区解析器。 */
    private LocaleResolver localeResolver;

    /** 当前分发器使用的主题解析器。 */
    private ThemeResolver themeResolver;

    /** 当前分发器使用的处理器映射器列表。 */
    private List<HandlerMapping> handlerMappings;

    /** 当前分发器使用的处理器适配器列表。 */
    private List<HandlerAdapter> handlerAdapters;

    /** 当前分发器使用的异常解析器列表。 */
    private List<HandlerExceptionResolver> handlerExceptionResolvers;

    /** 当前分发器使用的默认视图名称转换器。 */
    private RequestToViewNameTranslator viewNameTranslator;

    /** 当前分发器使用的视图解析器列表。 */
    private List<ViewResolver> viewResolvers;

    private boolean parseRequestPath;

    /** 供 Tomcat 等 Servlet 容器通过反射创建分发器。 */
    public MiniDispatcherServlet() {
        super(null);
    }

    public MiniDispatcherServlet(WebApplicationContext webApplicationContext) {
        super(webApplicationContext);
    }

    @Override
    protected void onRefresh(ApplicationContext context) {
        initStrategies(context);
    }

    /** 初始化此分发器使用的策略组件；子类可覆盖以增加自定义组件。 */
    protected void initStrategies(ApplicationContext context) {
        initMultipartResolver(context);
        initLocaleResolver(context);
        initThemeResolver(context);
        initHandlerMappings(context);
        initHandlerAdapters(context);
        initHandlerExceptionResolvers(context);
        initRequestToViewNameTranslator(context);
        initViewResolvers(context);
    }

    private void initMultipartResolver(ApplicationContext context) {
        try {
            this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
            if (logger.isTraceEnabled()) {
                logger.trace("Detected " + this.multipartResolver);
            }
            else if (logger.isDebugEnabled()) {
                logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
            }
        }
        catch (NoSuchBeanDefinitionException ex) {
            // 默认不配置文件上传解析器。
            this.multipartResolver = null;
            if (logger.isTraceEnabled()) {
                logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
            }
        }
    }

    private void initLocaleResolver(ApplicationContext context) {
        try {
            this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
            if (logger.isTraceEnabled()) {
                logger.trace("Detected " + this.localeResolver);
            }
            else if (logger.isDebugEnabled()) {
                logger.debug("Detected " + this.localeResolver.getClass().getSimpleName());
            }
        }
        catch (NoSuchBeanDefinitionException ex) {
            // 未配置区域解析器时，直接使用请求中的 Accept-Language。
            this.localeResolver = null;
        }
    }

    private void initThemeResolver(ApplicationContext context) {
        try {
            this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
            if (logger.isTraceEnabled()) {
                logger.trace("Detected " + this.themeResolver);
            }
            else if (logger.isDebugEnabled()) {
                logger.debug("Detected " + this.themeResolver.getClass().getSimpleName());
            }
        }
        catch (NoSuchBeanDefinitionException ex) {
            // 主题解析属于视图功能，不配置时保持关闭。
            this.themeResolver = null;
        }
    }

    private void initHandlerMappings(ApplicationContext context) {
        this.handlerMappings = null;

        // 按类型查找映射器；默认查找全部映射器。
        if (this.detectAllHandlerMappings) {
            // 查找当前容器及父容器中的全部 HandlerMapping。
            Map<String, HandlerMapping> matchingBeans =
                    BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
            if (!matchingBeans.isEmpty()) {
                this.handlerMappings = new ArrayList<>(matchingBeans.values());
                // 按顺序整理映射器。
                AnnotationAwareOrderComparator.sort(this.handlerMappings);
            }
        }
        // 根据名字（唯一）
        else {
            try {
                HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
                this.handlerMappings = Collections.singletonList(hm);
            }
            catch (NoSuchBeanDefinitionException ex) {
                // 未找到指定 Bean，稍后尝试加载默认映射器。
            }
        }

        // 未配置映射器时，从默认策略文件中读取实现类。
        if (this.handlerMappings == null) {
            this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
            if (logger.isTraceEnabled()) {
                logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
                        "': using default strategies from DispatcherServlet.properties");
            }
        }

        for (HandlerMapping mapping : this.handlerMappings) {
            if (mapping.usesPathPatterns()) {
                this.parseRequestPath = true;
                break;
            }
        }
    }

    private void initHandlerAdapters(ApplicationContext context) {
        this.handlerAdapters = null;

        if (this.detectAllHandlerAdapters) {
            // 查找当前容器及父容器中的全部 HandlerAdapter。
            Map<String, HandlerAdapter> matchingBeans =
                    BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
            if (!matchingBeans.isEmpty()) {
                this.handlerAdapters = new ArrayList<>(matchingBeans.values());
                // 按顺序整理适配器。
                AnnotationAwareOrderComparator.sort(this.handlerAdapters);
            }
        }
        else {
            try {
                HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
                this.handlerAdapters = Collections.singletonList(ha);
            }
            catch (NoSuchBeanDefinitionException ex) {
                // 未找到指定 Bean，稍后尝试加载默认适配器。
            }
        }

        // 未发现适配器时加载默认实现。
        if (this.handlerAdapters == null) {
            this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
            if (logger.isTraceEnabled()) {
                logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
                        "': using default strategies from DispatcherServlet.properties");
            }
        }
    }

    private void initHandlerExceptionResolvers(ApplicationContext context) {
        this.handlerExceptionResolvers = null;

        if (this.detectAllHandlerExceptionResolvers) {
            // 查找当前容器及父容器中的全部异常解析器。
            Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
                    .beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
            if (!matchingBeans.isEmpty()) {
                this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
                // 按顺序整理异常解析器。
                AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
            }
        }
        else {
            try {
                HandlerExceptionResolver her =
                        context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
                this.handlerExceptionResolvers = Collections.singletonList(her);
            }
            catch (NoSuchBeanDefinitionException ex) {
                // 未找到指定 Bean，稍后尝试加载默认异常解析器。
            }
        }

        // 未发现异常解析器时加载默认实现。
        if (this.handlerExceptionResolvers == null) {
            this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
            if (logger.isTraceEnabled()) {
                logger.trace("No HandlerExceptionResolvers declared in servlet '" + getServletName() +
                        "': using default strategies from DispatcherServlet.properties");
            }
        }
    }

    private void initRequestToViewNameTranslator(ApplicationContext context) {
        try {
            this.viewNameTranslator =
                    context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
            if (logger.isTraceEnabled()) {
                logger.trace("Detected " + this.viewNameTranslator.getClass().getSimpleName());
            }
            else if (logger.isDebugEnabled()) {
                logger.debug("Detected " + this.viewNameTranslator);
            }
        }
        catch (NoSuchBeanDefinitionException ex) {
            // 不配置视图名称转换器时，控制器直接负责写入响应。
            this.viewNameTranslator = null;
        }
    }

    private void initViewResolvers(ApplicationContext context) {
        this.viewResolvers = null;

        if (this.detectAllViewResolvers) {
            // 查找当前容器及父容器中的全部视图解析器。
            Map<String, ViewResolver> matchingBeans =
                    BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
            if (!matchingBeans.isEmpty()) {
                this.viewResolvers = new ArrayList<>(matchingBeans.values());
                // 按顺序整理视图解析器。
                AnnotationAwareOrderComparator.sort(this.viewResolvers);
            }
        }
        else {
            try {
                ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
                this.viewResolvers = Collections.singletonList(vr);
            }
            catch (NoSuchBeanDefinitionException ex) {
                // 未找到指定 Bean，稍后尝试加载默认视图解析器。
            }
        }

        // 未发现视图解析器时加载默认实现。
        if (this.viewResolvers == null) {
            this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
            if (logger.isTraceEnabled()) {
                logger.trace("No ViewResolvers declared for servlet '" + getServletName() +
                        "': using default strategies from DispatcherServlet.properties");
            }
        }
    }

    protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
        List<T> strategies = getDefaultStrategies(context, strategyInterface);
        if (strategies.size() != 1) {
            throw new BeanInitializationException(
                    "DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
        }
        return strategies.get(0);
    }

    protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
        if (defaultStrategies == null) {
            try {
        // 使用 PropertiesLoaderUtils 加载默认策略文件。
                ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, MiniDispatcherServlet.class);
                defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
            }
            catch (IOException ex) {
                throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
            }
        }

        String key = strategyInterface.getName();
        String value = defaultStrategies.getProperty(key);
        if (value != null) {
            String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
            List<T> strategies = new ArrayList<>(classNames.length);
            for (String className : classNames) {
                try {
                    Class<?> clazz = ClassUtils.forName(className, MiniDispatcherServlet.class.getClassLoader());
                    Object strategy = createDefaultStrategy(context, clazz);
                    strategies.add((T) strategy);
                }
                catch (ClassNotFoundException ex) {
                    throw new BeanInitializationException(
                            "Could not find DispatcherServlet's default strategy class [" + className +
                                    "] for interface [" + key + "]", ex);
                }
                catch (LinkageError err) {
                    throw new BeanInitializationException(
                            "Unresolvable class definition for DispatcherServlet's default strategy class [" +
                                    className + "] for interface [" + key + "]", err);
                }
            }
            return strategies;
        }
        else {
            return Collections.emptyList();
        }
    }

    protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
        return context.getAutowireCapableBeanFactory().createBean(clazz);
    }

    @Override
    protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Map<String, Object> attributesSnapshot = null;
        if (WebUtils.isIncludeRequest(request)) {
            attributesSnapshot = new HashMap<>();
            Enumeration<?> attrNames = request.getAttributeNames();
            while (attrNames.hasMoreElements()) {
                String attrName = (String) attrNames.nextElement();
                if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
                    attributesSnapshot.put(attrName, request.getAttribute(attrName));
                }
            }
        }

        // 将框架对象放入请求属性，供处理器和视图访问。
        request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
        request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
        request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
        RequestPath previousRequestPath = null;
        if (this.parseRequestPath) {
            previousRequestPath = (RequestPath) request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
            ServletRequestPathUtils.parseAndCache(request);
        }

        try {
            doDispatch(request, response);
        }
        finally {
            if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
                // Include 请求结束后恢复原始请求属性。
                if (attributesSnapshot != null) {
                    restoreAttributesAfterInclude(request, attributesSnapshot);
                }
            }
            if (this.parseRequestPath) {
                ServletRequestPathUtils.setParsedRequestPath(previousRequestPath, request);
            }
        }
    }

    protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpServletRequest processedRequest = request;
        HandlerExecutionChain mappedHandler = null;
        boolean multipartRequestParsed = false;

        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

        try {
            ModelAndView mv = null;
            Exception dispatchException = null;

            try {
                processedRequest = checkMultipart(request);
                multipartRequestParsed = (processedRequest != request);

                // 进行映射
                mappedHandler = getHandler(processedRequest);
                if (mappedHandler == null) {
                    noHandlerFound(processedRequest, response);
                    return;
                }

                // 找到最合适的HandlerAdapter
                HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

                // 处理器支持时检查 Last-Modified 缓存头。
                String method = request.getMethod();
                boolean isGet = HttpMethod.GET.matches(method);
                if (isGet || HttpMethod.HEAD.matches(method)) {
                    long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
                    if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
                        return;
                    }
                }
                // 前置拦截器
                if (!mappedHandler.applyPreHandle(processedRequest, response)) {
                    // 返回false就不进行后续处理了
                    return;
                }

                // 调用处理器。
                mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

                if (asyncManager.isConcurrentHandlingStarted()) {
                    return;
                }
                // 如果mv有  视图没有，给你设置默认视图
                applyDefaultViewName(processedRequest, mv);
                //后置拦截器
                mappedHandler.applyPostHandle(processedRequest, response, mv);
            }
            catch (Exception ex) {
                dispatchException = ex;
            }
            catch (Throwable err) {
                // 将处理器方法抛出的 Error 也交由异常处理流程处理。
                dispatchException = new NestedServletException("Handler dispatch failed", err);
            }
            // 渲染视图
            processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
        }
        catch (Exception ex) {
            triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
        }
        catch (Throwable err) {
            triggerAfterCompletion(processedRequest, response, mappedHandler,
                    new NestedServletException("Handler processing failed", err));
        }
        finally {
            if (asyncManager.isConcurrentHandlingStarted()) {
                // 异步处理期间不在此处调用常规后置拦截器。
                if (mappedHandler != null) {
                    mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
                }
            }
            else {
                // 清理文件上传请求使用的临时资源。
                if (multipartRequestParsed) {
                    cleanupMultipart(processedRequest);
                }
            }
        }
    }

    protected void cleanupMultipart(HttpServletRequest request) {
        if (this.multipartResolver != null) {
            MultipartHttpServletRequest multipartRequest =
                    WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
            if (multipartRequest != null) {
                this.multipartResolver.cleanupMultipart(multipartRequest);
            }
        }
    }

    private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

        if (mappedHandler != null) {
            mappedHandler.triggerAfterCompletion(request, response, ex);
        }
        throw ex;
    }

    private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
                                       HandlerExecutionChain mappedHandler, ModelAndView mv,
                                       Exception exception) throws Exception {

        boolean errorView = false;

        // 异常视图
        if (exception != null) {
            if (exception instanceof ModelAndViewDefiningException) {
                logger.debug("ModelAndViewDefiningException encountered", exception);
                mv = ((ModelAndViewDefiningException) exception).getModelAndView();
            }
            else {
                Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
                mv = processHandlerException(request, response, handler, exception);
                errorView = (mv != null);
            }
        }

        // 处理器是否返回了待渲染的视图？
        if (mv != null && !mv.wasCleared()) {
            // 解析、渲染视图
            render(mv, request, response);
            if (errorView) {
                WebUtils.clearErrorRequestAttributes(request);
            }
        }
        else {
            if (logger.isTraceEnabled()) {
                logger.trace("No view rendering, null ModelAndView returned.");
            }
        }

        if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
            // 转发过程中启动了异步处理。
            return;
        }

        if (mappedHandler != null) {
                // 异常已处理时，执行拦截器的完成回调。
            mappedHandler.triggerAfterCompletion(request, response, null);
        }
    }

    protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 解析请求地区设置并应用到响应。
        Locale locale =
                (this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
        response.setLocale(locale);

        View view;
        String viewName = mv.getViewName();
        if (viewName != null) {
            // 解析视图名
            view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
            if (view == null) {
                throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
                        "' in servlet with name '" + getServletName() + "'");
            }
        }
        else {
        // 视图模型已包含 View 实例，无需再次解析。
            view = mv.getView();
            if (view == null) {
                throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
                        "View object in servlet with name '" + getServletName() + "'");
            }
        }

        // 委托 View 对象渲染响应。
        if (logger.isTraceEnabled()) {
            logger.trace("Rendering view [" + view + "] ");
        }
        try {
            if (mv.getStatus() != null) {
                response.setStatus(mv.getStatus().value());
            }
            view.render(mv.getModelInternal(), request, response);
        }
        catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Error rendering view [" + view + "]", ex);
            }
            throw ex;
        }
    }

    protected View resolveViewName(String viewName, Map<String, Object> model,
                                   Locale locale, HttpServletRequest request) throws Exception {

        if (this.viewResolvers != null) {
            for (ViewResolver viewResolver : this.viewResolvers) {
                View view = viewResolver.resolveViewName(viewName, locale);
                if (view != null) {
                    return view;
                }
            }
        }
        return null;
    }

    protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
                                                   Object handler, Exception ex) throws Exception {

        // 成功响应和错误响应可能使用不同的媒体类型。
        request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);

        // 依次检查已注册的异常解析器。
        ModelAndView exMv = null;
        if (this.handlerExceptionResolvers != null) {
            for (HandlerExceptionResolver resolver : this.handlerExceptionResolvers) {
                exMv = resolver.resolveException(request, response, handler, ex);
                if (exMv != null) {
                    break;
                }
            }
        }
        if (exMv != null) {
            if (exMv.isEmpty()) {
                request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
                return null;
            }
        // 普通错误模型仍可能需要转换默认视图名称。
            if (!exMv.hasView()) {
                String defaultViewName = getDefaultViewName(request);
                if (defaultViewName != null) {
                    exMv.setViewName(defaultViewName);
                }
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Using resolved error view: " + exMv, ex);
            }
            else if (logger.isDebugEnabled()) {
                logger.debug("Using resolved error view: " + exMv);
            }
            WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
            return exMv;
        }

        throw ex;
    }

    /**
     * 如果没有视图，给你设置默认视图
     */
    private void applyDefaultViewName(HttpServletRequest request, ModelAndView mv) throws Exception {
        if (mv != null && !mv.hasView()) {
            String defaultViewName = getDefaultViewName(request);
            if (defaultViewName != null) {
                mv.setViewName(defaultViewName);
            }
        }
    }

    protected String getDefaultViewName(HttpServletRequest request) throws Exception {
        return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
    }

    protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
        if (this.handlerAdapters != null) {
            for (HandlerAdapter adapter : this.handlerAdapters) {
                if (adapter.supports(handler)) {
                    return adapter;
                }
            }
        }
        throw new ServletException("No adapter for handler [" + handler +
                "]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
    }

    protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (pageNotFoundLogger.isWarnEnabled()) {
            pageNotFoundLogger.warn("No mapping for " + request.getMethod() + " " + getRequestUri(request));
        }
        if (this.throwExceptionIfNoHandlerFound) {
            throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
                    new ServletServerHttpRequest(request).getHeaders());
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static String getRequestUri(HttpServletRequest request) {
        String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
        if (uri == null) {
            uri = request.getRequestURI();
        }
        return uri;
    }

    protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        if (this.handlerMappings != null) {
            // 按顺序查询映射器，使用首个匹配当前请求的处理器。
            for (HandlerMapping mapping : this.handlerMappings) {
                HandlerExecutionChain handler = mapping.getHandler(request);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return null;
    }

    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
            if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
                if (DispatcherType.REQUEST.equals(request.getDispatcherType())) {
                    logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
                }
            }
            else if (hasMultipartException(request)) {
                logger.debug("Multipart resolution previously failed for current request - " +
                        "skipping re-resolution for undisturbed error rendering");
            }
            else {
                try {
                    return this.multipartResolver.resolveMultipart(request);
                }
                catch (MultipartException ex) {
                    if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
                        logger.debug("Multipart resolution failed for error dispatch", ex);
                        // 按普通请求处理流程继续处理错误分派。
                    }
                    else {
                        throw ex;
                    }
                }
            }
        }
        // 未创建包装请求时，返回原始请求。
        return request;
    }

    private boolean hasMultipartException(HttpServletRequest request) {
        Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
        while (error != null) {
            if (error instanceof MultipartException) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }

    private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
        // 先复制到独立集合，避免删除属性时影响 Enumeration。
        Set<String> attrsToCheck = new HashSet<>();
        Enumeration<?> attrNames = request.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            String attrName = (String) attrNames.nextElement();
            if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
                attrsToCheck.add(attrName);
            }
        }

        // 恢复可能已被移除的属性。
        attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

        // 遍历待恢复属性，恢复原值或删除本次新增的属性。
        for (String attrName : attrsToCheck) {
            Object attrValue = attributesSnapshot.get(attrName);
            if (attrValue == null) {
                request.removeAttribute(attrName);
            }
            else if (attrValue != request.getAttribute(attrName)) {
                request.setAttribute(attrName, attrValue);
            }
        }
    }

}
