package com.yrainy.web.servlet.support;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.context.support.WebApplicationObjectSupport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 为请求处理器提供 HTTP 方法校验、会话校验和响应缓存头配置能力。
 * @author zhanglun
 * @since 2026-09-03
 */
public abstract class WebContentGenerator extends WebApplicationObjectSupport {

    /** HTTP GET 方法名称。 */
    public static final String METHOD_GET = "GET";

    /** HTTP HEAD 方法名称。 */
    public static final String METHOD_HEAD = "HEAD";

    /** HTTP POST 方法名称。 */
    public static final String METHOD_POST = "POST";

    private static final String HEADER_PRAGMA = "Pragma";

    private static final String HEADER_EXPIRES = "Expires";

    protected static final String HEADER_CACHE_CONTROL = "Cache-Control";

    /** 当前处理器支持的 HTTP 方法集合。 */
    private Set<String> supportedMethods;
    private String allowHeader;

    private boolean requireSession = false;
    private CacheControl cacheControl;

    private int cacheSeconds = -1;
    private String[] varyByRequestHeaders;

    public WebContentGenerator() {
        this(true);
    }

    public WebContentGenerator(boolean restrictDefaultSupportedMethods) {
        if (restrictDefaultSupportedMethods) {
            this.supportedMethods = new LinkedHashSet<>(4);
            this.supportedMethods.add(METHOD_GET);
            this.supportedMethods.add(METHOD_HEAD);
            this.supportedMethods.add(METHOD_POST);
        }
        initAllowHeader();
    }

    public WebContentGenerator(String... supportedMethods) {
        setSupportedMethods(supportedMethods);
    }

    public final void setSupportedMethods(String... methods) {
        if (!ObjectUtils.isEmpty(methods)) {
            this.supportedMethods = new LinkedHashSet<>(Arrays.asList(methods));
        }
        else {
            this.supportedMethods = null;
        }
        initAllowHeader();
    }

    private void initAllowHeader() {
        Collection<String> allowedMethods;
        if (this.supportedMethods == null) {
            allowedMethods = new ArrayList<>(HttpMethod.values().length - 1);
            for (HttpMethod method : HttpMethod.values()) {
                if (method != HttpMethod.TRACE) {
                    allowedMethods.add(method.name());
                }
            }
        }
        else if (this.supportedMethods.contains(HttpMethod.OPTIONS.name())) {
            allowedMethods = this.supportedMethods;
        }
        else {
            allowedMethods = new ArrayList<>(this.supportedMethods);
            allowedMethods.add(HttpMethod.OPTIONS.name());

        }
        this.allowHeader = StringUtils.collectionToCommaDelimitedString(allowedMethods);
    }


    protected String getAllowHeader() {
        return this.allowHeader;
    }

    public final void setCacheControl(CacheControl cacheControl) {
        this.cacheControl = cacheControl;
    }

    public final CacheControl getCacheControl() {
        return this.cacheControl;
    }

    /**
     * 设置响应内容的缓存时间，并根据秒数生成缓存相关 HTTP 响应头：
     * <ul>
     * <li>seconds == -1（默认值）：不生成缓存相关响应头</li>
     * <li>seconds == 0：通过 {@code Cache-Control: no-store} 禁止缓存</li>
     * <li>seconds > 0：通过 {@code Cache-Control: max-age=秒数} 请求缓存</li>
     * </ul>
     * <p>需要更细致的策略时，请使用自定义 {@link org.springframework.http.CacheControl}。
     * @see #setCacheControl
     */
    public final void setCacheSeconds(int seconds) {
        this.cacheSeconds = seconds;
    }

    /**
     * 返回内容的缓存秒数。
     */
    public final int getCacheSeconds() {
        return this.cacheSeconds;
    }


    public final void setVaryByRequestHeaders(String... varyByRequestHeaders) {
        this.varyByRequestHeaders = varyByRequestHeaders;
    }


    /** 检查请求方法是否受支持，以及请求是否满足会话要求。 */
    protected final void checkRequest(HttpServletRequest request) throws ServletException {
        // 检查当前处理器是否支持该请求方法。
        String method = request.getMethod();
        if (this.supportedMethods != null && !this.supportedMethods.contains(method)) {
            throw new HttpRequestMethodNotSupportedException(method, this.supportedMethods);
        }

        // 检查是否必须存在已有会话。
        if (this.requireSession && request.getSession(false) == null) {
            throw new HttpSessionRequiredException("Pre-existing session required but none found");
        }
    }

    /**
     * 根据当前生成器的设置准备响应，包括缓存策略和 Vary 响应头。
     * @param response 当前 HTTP 响应
     * @since 4.2
     */
    protected final void prepareResponse(HttpServletResponse response) {
        if (this.cacheControl != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Applying default " + getCacheControl());
            }
            applyCacheControl(response, this.cacheControl);
        }
        else {
            if (logger.isTraceEnabled()) {
                logger.trace("Applying default cacheSeconds=" + this.cacheSeconds);
            }
            applyCacheSeconds(response, this.cacheSeconds);
        }
        if (this.varyByRequestHeaders != null) {
            for (String value : getVaryRequestHeadersToAdd(response, this.varyByRequestHeaders)) {
                response.addHeader("Vary", value);
            }
        }
    }

    protected final void applyCacheControl(HttpServletResponse response, CacheControl cacheControl) {
        String ccValue = cacheControl.getHeaderValue();
        if (ccValue != null) {
            // 设置计算得到的 HTTP 1.1 Cache-Control 响应头。
            response.setHeader(HEADER_CACHE_CONTROL, ccValue);

            if (response.containsHeader(HEADER_PRAGMA)) {
                // 清除已有的 HTTP 1.0 Pragma 响应头。
                response.setHeader(HEADER_PRAGMA, "");
            }
            if (response.containsHeader(HEADER_EXPIRES)) {
                // 清除已有的 HTTP 1.0 Expires 响应头。
                response.setHeader(HEADER_EXPIRES, "");
            }
        }
    }

    /** 根据缓存秒数写入 Cache-Control 响应头。 */
    protected final void applyCacheSeconds(HttpServletResponse response, int cacheSeconds) {
        if (cacheSeconds > 0) {
            applyCacheControl(response, CacheControl.maxAge(cacheSeconds, TimeUnit.SECONDS));
        }
        else if (cacheSeconds == 0) {
            applyCacheControl(response, CacheControl.noStore());
        }
    }

    private Collection<String> getVaryRequestHeadersToAdd(HttpServletResponse response, String[] varyByRequestHeaders) {
        if (!response.containsHeader(HttpHeaders.VARY)) {
            return Arrays.asList(varyByRequestHeaders);
        }
        Collection<String> result = new ArrayList<>(varyByRequestHeaders.length);
        Collections.addAll(result, varyByRequestHeaders);
        for (String header : response.getHeaders(HttpHeaders.VARY)) {
            for (String existing : StringUtils.tokenizeToStringArray(header, ",")) {
                if ("*".equals(existing)) {
                    return Collections.emptyList();
                }
                for (String value : varyByRequestHeaders) {
                    if (value.equalsIgnoreCase(existing)) {
                        result.remove(value);
                    }
                }
            }
        }
        return result;
    }



}
