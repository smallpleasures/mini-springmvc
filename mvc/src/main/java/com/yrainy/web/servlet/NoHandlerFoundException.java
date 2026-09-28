package com.yrainy.web.servlet;

import org.springframework.http.HttpHeaders;

import javax.servlet.ServletException;

public class NoHandlerFoundException extends ServletException {

	private final String httpMethod;

	private final String requestURL;

	private final HttpHeaders headers;


	/**
	 * 创建未找到处理器异常。
	 *
	 * @param httpMethod HTTP 请求方法
	 * @param requestURL HTTP 请求地址
	 * @param headers HTTP 请求头
	 */
	public NoHandlerFoundException(String httpMethod, String requestURL, HttpHeaders headers) {
		super("No handler found for " + httpMethod + " " + requestURL);
		this.httpMethod = httpMethod;
		this.requestURL = requestURL;
		this.headers = headers;
	}


	public String getHttpMethod() {
		return this.httpMethod;
	}

	public String getRequestURL() {
		return this.requestURL;
	}

	public HttpHeaders getHeaders() {
		return this.headers;
	}

}
