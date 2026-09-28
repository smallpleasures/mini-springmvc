
package com.yrainy.web.servlet.support;

import com.yrainy.web.servlet.LocaleContextResolver;
import com.yrainy.web.servlet.LocaleResolver;
import com.yrainy.web.servlet.MiniDispatcherServlet;
import com.yrainy.web.servlet.ThemeResolver;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;
import org.springframework.ui.context.Theme;
import org.springframework.ui.context.ThemeSource;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 用于便捷读取请求级状态的工具类；这些状态由 {@link MiniDispatcherServlet} 绑定到请求。
 *
 * <p>提供 WebApplicationContext、LocaleResolver、Locale、ThemeResolver 和 Theme 的查询方法。
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 03.03.2003
 * @see RequestContext
 * @see org.springframework.web.servlet.DispatcherServlet
 */
public abstract class RequestContextUtils {

	/**
	 * 用于查找 {@link RequestDataValueProcessor} 实现的 Bean 名称。
	 * @since 4.2.1
	 */
	public static final String REQUEST_DATA_VALUE_PROCESSOR_BEAN_NAME = "requestDataValueProcessor";


	/**
	 * 查找发起当前请求的 MiniDispatcherServlet 关联的 WebApplicationContext；
	 * 若请求中没有该上下文，则从 ServletContext 或 ContextLoader 查找全局上下文。
	 * <p>显式传入 ServletContext 的重载形式兼容 Servlet 2.5。
	 * @param request 当前 HTTP 请求
	 * @param servletContext 当前 Servlet 上下文
	 * @return 请求级上下文或全局上下文；均未找到时返回 {@code null}
	 * @since 4.2.1
	 * @see DispatcherServlet#WEB_APPLICATION_CONTEXT_ATTRIBUTE
	 * @see WebApplicationContextUtils#getWebApplicationContext(ServletContext)
	 * @see ContextLoader#getCurrentWebApplicationContext()
	 */
	public static WebApplicationContext findWebApplicationContext(
			HttpServletRequest request, ServletContext servletContext) {

		WebApplicationContext webApplicationContext = (WebApplicationContext) request.getAttribute(
				MiniDispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (webApplicationContext == null) {
			if (servletContext != null) {
				webApplicationContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);
			}
			if (webApplicationContext == null) {
				webApplicationContext = ContextLoader.getCurrentWebApplicationContext();
			}
		}
		return webApplicationContext;
	}

	/**
	 * 查找发起当前请求的 MiniDispatcherServlet 关联的 WebApplicationContext；
	 * 若请求中没有该上下文，则从 ServletContext 或 ContextLoader 查找全局上下文。
	 * <p>此方法要求 Servlet 3.0 或更高版本。
	 * @param request 当前 HTTP 请求
	 * @return 请求级上下文或全局上下文；均未找到时返回 {@code null}
	 * @since 4.2.1
	 * @see #findWebApplicationContext(HttpServletRequest, ServletContext)
	 * @see ServletRequest#getServletContext()
	 * @see ContextLoader#getCurrentWebApplicationContext()
	 */
	public static WebApplicationContext findWebApplicationContext(HttpServletRequest request) {
		return findWebApplicationContext(request, request.getServletContext());
	}

	/**
	 * 返回 MiniDispatcherServlet 绑定到请求的 LocaleResolver。
	 * @param request 当前 HTTP 请求
	 * @return 当前 LocaleResolver；未找到时返回 {@code null}
	 */
	public static LocaleResolver getLocaleResolver(HttpServletRequest request) {
		return (LocaleResolver) request.getAttribute(MiniDispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE);
	}

	/**
	 * 获取请求的当前 Locale。优先使用 MiniDispatcherServlet 绑定的 LocaleResolver，
	 * 未配置时回退到请求的 Accept-Language 信息。
	 * @param request 当前 HTTP 请求
	 * @return 当前 Locale，来自 LocaleResolver 或请求本身
	 * @see #getLocaleResolver
	 * @see org.springframework.context.i18n.LocaleContextHolder#getLocale()
	 */
	public static Locale getLocale(HttpServletRequest request) {
		LocaleResolver localeResolver = getLocaleResolver(request);
		return (localeResolver != null ? localeResolver.resolveLocale(request) : request.getLocale());
	}

	/**
	 * 获取请求的当前时区。仅当 LocaleResolver 实现 LocaleContextResolver 且提供了时区时才返回结果；
	 * 未提供时返回 {@code null}。
	 * @param request 当前 HTTP 请求
	 * @return 当前时区；未关联时区时返回 {@code null}
	 * @see #getLocaleResolver
	 * @see org.springframework.context.i18n.LocaleContextHolder#getTimeZone()
	 */
	public static TimeZone getTimeZone(HttpServletRequest request) {
		LocaleResolver localeResolver = getLocaleResolver(request);
		if (localeResolver instanceof LocaleContextResolver) {
			LocaleContext localeContext = ((LocaleContextResolver) localeResolver).resolveLocaleContext(request);
			if (localeContext instanceof TimeZoneAwareLocaleContext) {
				return ((TimeZoneAwareLocaleContext) localeContext).getTimeZone();
			}
		}
		return null;
	}

	/**
	 * 返回 MiniDispatcherServlet 绑定到请求的 ThemeResolver。
	 * @param request 当前 HTTP 请求
	 * @return 当前 ThemeResolver；未找到时返回 {@code null}
	 */
	public static ThemeResolver getThemeResolver(HttpServletRequest request) {
		return (ThemeResolver) request.getAttribute(MiniDispatcherServlet.THEME_RESOLVER_ATTRIBUTE);
	}

	/**
	 * 返回 MiniDispatcherServlet 绑定到请求的 ThemeSource。
	 * @param request 当前 HTTP 请求
	 * @return 当前 ThemeSource
	 */
	public static ThemeSource getThemeSource(HttpServletRequest request) {
		return (ThemeSource) request.getAttribute(MiniDispatcherServlet.THEME_SOURCE_ATTRIBUTE);
	}

	/**
	 * 使用请求关联的 ThemeResolver 和 ThemeSource 获取当前主题。
	 * @param request 当前 HTTP 请求
	 * @return 当前主题；未找到时返回 {@code null}
	 * @see #getThemeResolver
	 */
	public static Theme getTheme(HttpServletRequest request) {
		ThemeResolver themeResolver = getThemeResolver(request);
		ThemeSource themeSource = getThemeSource(request);
		if (themeResolver != null && themeSource != null) {
			String themeName = themeResolver.resolveThemeName(request);
			return themeSource.getTheme(themeName);
		}
		else {
			return null;
		}
	}

	/**
	 * 返回本次请求携带的只读输入 Flash 属性。
	 * @param request 当前请求
	 * @return 只读属性映射；未找到时返回 {@code null}
	 * @see FlashMap
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, ?> getInputFlashMap(HttpServletRequest request) {
		return (Map<String, ?>) request.getAttribute(MiniDispatcherServlet.INPUT_FLASH_MAP_ATTRIBUTE);
	}

}
