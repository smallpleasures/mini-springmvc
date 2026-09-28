package com.yrainy.web.servlet.handler;

import com.yrainy.web.servlet.support.RequestContextUtils;
import org.springframework.web.context.request.ServletWebRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

public class DispatcherServletWebRequest extends ServletWebRequest {

	/** 根据当前请求创建 Web 请求包装对象。 */
	public DispatcherServletWebRequest(HttpServletRequest request) {
		super(request);
	}

	/** 根据当前请求和响应创建 Web 请求包装对象。 */
	public DispatcherServletWebRequest(HttpServletRequest request, HttpServletResponse response) {
		super(request, response);
	}

	@Override
	public Locale getLocale() {
		return RequestContextUtils.getLocale(getRequest());
	}

}
