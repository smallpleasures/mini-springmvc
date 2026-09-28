package com.yrainy.web.servlet.mvc.condition;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.cors.CorsUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/** 匹配并比较 HTTP 请求方法条件。 */
public final class RequestMethodsRequestCondition
		extends AbstractRequestCondition<RequestMethodsRequestCondition> {

	private final Set<RequestMethod> methods;

	public RequestMethodsRequestCondition(RequestMethod... methods) {
		Set<RequestMethod> values = new LinkedHashSet<>(Arrays.asList(methods));
		this.methods = Collections.unmodifiableSet(values);
	}

	private RequestMethodsRequestCondition(Set<RequestMethod> methods) {
		this.methods = Collections.unmodifiableSet(methods);
	}

	public Set<RequestMethod> getMethods() {
		return this.methods;
	}

	@Override
	protected Collection<?> getContent() {
		return this.methods;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	@Override
	public RequestMethodsRequestCondition combine(RequestMethodsRequestCondition other) {
		if (this.methods.isEmpty()) {
			return other;
		}
		if (other.methods.isEmpty()) {
			return this;
		}
		Set<RequestMethod> intersection = new LinkedHashSet<>(this.methods);
		intersection.retainAll(other.methods);
		return new RequestMethodsRequestCondition(intersection);
	}

	@Override
	public RequestMethodsRequestCondition getMatchingCondition(HttpServletRequest request) {
		if (this.methods.isEmpty()) {
			return this;
		}
		String methodName = (CorsUtils.isPreFlightRequest(request) ?
				request.getHeader("Access-Control-Request-Method") : request.getMethod());
		RequestMethod method;
		try {
			method = RequestMethod.valueOf(methodName);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return null;
		}
		if (this.methods.contains(method)) {
			return this;
		}
		// HEAD 请求可由 GET 映射处理，Servlet 容器会忽略响应正文。
		return (method == RequestMethod.HEAD && this.methods.contains(RequestMethod.GET) ? this : null);
	}

	@Override
	public int compareTo(RequestMethodsRequestCondition other, HttpServletRequest request) {
		if (this.methods.isEmpty() && !other.methods.isEmpty()) {
			return 1;
		}
		if (!this.methods.isEmpty() && other.methods.isEmpty()) {
			return -1;
		}
		return Integer.compare(other.methods.size(), this.methods.size());
	}
}
