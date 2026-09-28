package com.yrainy.web.servlet.handler;

import com.yrainy.web.servlet.HandlerExecutionChain;
import com.yrainy.web.servlet.HandlerInterceptor;
import com.yrainy.web.servlet.HandlerMapping;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.RequestPath;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.cors.*;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 处理器映射的抽象基类，负责查找处理器并组装拦截器执行链。
 * @author zhanglun
 * @since 2026-09-02
 */
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport implements HandlerMapping, Ordered {
    private Object defaultHandler;
    private int order = Ordered.LOWEST_PRECEDENCE;
    private PathPatternParser patternParser;

    private UrlPathHelper urlPathHelper = new UrlPathHelper();

    private PathMatcher pathMatcher = new AntPathMatcher();

    private final List<Object> interceptors = new ArrayList<>();

    private final List<HandlerInterceptor> adaptedInterceptors = new ArrayList<>();
    private CorsConfigurationSource corsConfigurationSource;

    private CorsProcessor corsProcessor = new DefaultCorsProcessor();

    /** 设置处理器映射的优先级，数值越小优先级越高。 */
    public void setOrder(int order) {
        this.order = order;
    }

    /** 返回处理器映射的优先级。 */
    @Override
    public int getOrder() {
        return this.order;
    }

    public void setDefaultHandler(Object defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    public Object getDefaultHandler() {
        return this.defaultHandler;
    }

    public void setPatternParser(PathPatternParser patternParser) {
        this.patternParser = patternParser;
    }

    public PathPatternParser getPatternParser() {
        return this.patternParser;
    }


    public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
        Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
        this.urlPathHelper = urlPathHelper;
        if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
            ((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlPathHelper(urlPathHelper);
        }
    }

    public UrlPathHelper getUrlPathHelper() {
        return this.urlPathHelper;
    }

    public void setPathMatcher(PathMatcher pathMatcher) {
        Assert.notNull(pathMatcher, "PathMatcher must not be null");
        this.pathMatcher = pathMatcher;
        if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
            ((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setPathMatcher(pathMatcher);
        }
    }

    public PathMatcher getPathMatcher() {
        return this.pathMatcher;
    }


    public void setInterceptors(Object... interceptors) {
        this.interceptors.addAll(Arrays.asList(interceptors));
    }

    public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
        if (CollectionUtils.isEmpty(corsConfigurations)) {
            this.corsConfigurationSource = null;
            return;
        }
        UrlBasedCorsConfigurationSource source;
        if (getPatternParser() != null) {
            source = new UrlBasedCorsConfigurationSource(getPatternParser());
            source.setCorsConfigurations(corsConfigurations);
        }
        else {
            source = new UrlBasedCorsConfigurationSource();
            source.setCorsConfigurations(corsConfigurations);
            source.setPathMatcher(this.pathMatcher);
            source.setUrlPathHelper(this.urlPathHelper);
        }
        setCorsConfigurationSource(source);
    }

    public void setCorsConfigurationSource(CorsConfigurationSource source) {
        Assert.notNull(source, "CorsConfigurationSource must not be null");
        this.corsConfigurationSource = source;
        if (source instanceof UrlBasedCorsConfigurationSource) {
            ((UrlBasedCorsConfigurationSource) source).setAllowInitLookupPath(false);
        }
    }

    public CorsConfigurationSource getCorsConfigurationSource() {
        return this.corsConfigurationSource;
    }

    @Override
    protected void initApplicationContext() throws BeansException {
        // 初始化映射拦截器和显式配置的拦截器。
        detectMappedInterceptors(this.adaptedInterceptors);
        initInterceptors();
    }

    protected void detectMappedInterceptors(List<HandlerInterceptor> mappedInterceptors) {
        mappedInterceptors.addAll(BeanFactoryUtils.beansOfTypeIncludingAncestors(
                obtainApplicationContext(), MappedInterceptor.class, true, false).values());
    }

    protected void initInterceptors() {
        if (!this.interceptors.isEmpty()) {
            for (int i = 0; i < this.interceptors.size(); i++) {
                Object interceptor = this.interceptors.get(i);
                if (interceptor == null) {
                    throw new IllegalArgumentException("Entry number " + i + " in interceptors array is null");
                }
                this.adaptedInterceptors.add(adaptInterceptor(interceptor));
            }
        }
    }

    protected HandlerInterceptor adaptInterceptor(Object interceptor) {
        if (interceptor instanceof HandlerInterceptor) {
            return (HandlerInterceptor) interceptor;
        }
        else if (interceptor instanceof WebRequestInterceptor) {
            return new WebRequestHandlerInterceptorAdapter((WebRequestInterceptor) interceptor);
        }
        else {
            throw new IllegalArgumentException("Interceptor type not supported: " + interceptor.getClass().getName());
        }
    }

    public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        Object handler = getHandlerInternal(request);
        if (handler == null) {
            handler = getDefaultHandler();
        }
        if (handler == null) {
            return null;
        }
        // 配置值可能是 Bean 名称，也可能已经是处理器实例。
        if (handler instanceof String) {
            String handlerName = (String) handler;
            handler = obtainApplicationContext().getBean(handlerName);
        }

        // 确保已缓存查找路径，供拦截器及其他组件使用。
        if (!ServletRequestPathUtils.hasCachedPath(request)) {
            initLookupPath(request);
        }

        HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);

        if (logger.isTraceEnabled()) {
            logger.trace("Mapped to " + handler);
        }
        else if (logger.isDebugEnabled() && !DispatcherType.ASYNC.equals(request.getDispatcherType())) {
            logger.debug("Mapped to " + executionChain.getHandler());
        }

        return executionChain;
    }

    protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;


    protected String initLookupPath(HttpServletRequest request) {
        if (usesPathPatterns()) {
            request.removeAttribute(UrlPathHelper.PATH_ATTRIBUTE);
            RequestPath requestPath = ServletRequestPathUtils.getParsedRequestPath(request);
            String lookupPath = requestPath.pathWithinApplication().value();
            return UrlPathHelper.defaultInstance.removeSemicolonContent(lookupPath);
        }
        else {
            return getUrlPathHelper().resolveAndCacheLookupPath(request);
        }
    }

    protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
        HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
                (HandlerExecutionChain) handler : new HandlerExecutionChain(handler));

        for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
            if (interceptor instanceof MappedInterceptor) {
                MappedInterceptor mappedInterceptor = (MappedInterceptor) interceptor;
                if (mappedInterceptor.matches(request)) {
                    chain.addInterceptor(mappedInterceptor.getInterceptor());
                }
            }
            else {
                chain.addInterceptor(interceptor);
            }
        }
        return chain;
    }

    protected boolean hasCorsConfigurationSource(Object handler) {
        if (handler instanceof HandlerExecutionChain) {
            handler = ((HandlerExecutionChain) handler).getHandler();
        }
        return (handler instanceof CorsConfigurationSource || this.corsConfigurationSource != null);
    }

    protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
        Object resolvedHandler = handler;
        if (handler instanceof HandlerExecutionChain) {
            resolvedHandler = ((HandlerExecutionChain) handler).getHandler();
        }
        if (resolvedHandler instanceof CorsConfigurationSource) {
            return ((CorsConfigurationSource) resolvedHandler).getCorsConfiguration(request);
        }
        return null;
    }




}
