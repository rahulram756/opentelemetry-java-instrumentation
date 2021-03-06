/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ServletHttpServerTracer<RESPONSE>
		extends HttpServerTracer<HttpServletRequest, RESPONSE, HttpServletRequest, HttpServletRequest> {

	private static final Logger log = LoggerFactory.getLogger(ServletHttpServerTracer.class);
	public static final String ZKID = "ZK-SID";
	private Context parentContext;

	public Context startSpan(HttpServletRequest request) {
		String spanName = getSpanName(request);
		if(spanName ==  null){
			return null;
		}
		if(this.parentContext != null){
		return startSpan(request, request, spanName, this.parentContext);
		}
		return startSpan(request, request, spanName);
	}

	@Override
	public Scope startScope(Span span, HttpServletRequest request) {
		Context context = Context.current();
		String contextPath = request.getContextPath();
		
		if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
			context = context.with(ServletContextPath.CONTEXT_KEY, contextPath);
		}
		//Changes
		context = super.startContext(span, request, context);
		String zkId = request.getHeader(ZKID);
		if (zkId != null) {
			request.setAttribute(zkId, context);
		} else {
			request.setAttribute(request.getSession().getId().toString(), context);
		}
		return context.makeCurrent();
	}

	@Override
	protected String url(HttpServletRequest httpServletRequest) {
		try {
			return new URI(httpServletRequest.getScheme(), null, httpServletRequest.getServerName(),
					httpServletRequest.getServerPort(), httpServletRequest.getRequestURI(),
					httpServletRequest.getQueryString(), null).toString();
		} catch (URISyntaxException e) {
			log.debug("Failed to construct request URI", e);
			return null;
		}
	}

	@Override
	public Context getServerContext(HttpServletRequest request) {
		Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
		return context instanceof Context ? (Context) context : null;
	}

	@Override
	protected void attachServerContext(Context context, HttpServletRequest request) {
		request.setAttribute(CONTEXT_ATTRIBUTE, context);
	}

	@Override
	protected Integer peerPort(HttpServletRequest connection) {
		// HttpServletResponse doesn't have accessor for remote port prior to
		// Servlet spec 3.0
		return null;
	}

	@Override
	protected String peerHostIP(HttpServletRequest connection) {
		return connection.getRemoteAddr();
	}

	@Override
	protected String method(HttpServletRequest request) {
		return request.getMethod();
	}

	@Override
	public void onRequest(Span span, HttpServletRequest request) {
		// we do this e.g. so that servlet containers can use these values in
		// their access logs
		request.setAttribute("traceId", span.getSpanContext().getTraceIdAsHexString());
		request.setAttribute("spanId", span.getSpanContext().getSpanIdAsHexString());

		super.onRequest(span, request);
	}

	@Override
	protected Getter<HttpServletRequest> getGetter() {
		return HttpServletRequestGetter.GETTER;
	}

	@Override
	protected Throwable unwrapThrowable(Throwable throwable) {
		Throwable result = throwable;
		if (throwable instanceof ServletException && throwable.getCause() != null) {
			result = throwable.getCause();
		}
		return super.unwrapThrowable(result);
	}

	public void setPrincipal(Span span, HttpServletRequest request) {
		Principal principal = request.getUserPrincipal();
		if (principal != null) {
			span.setAttribute(SemanticAttributes.ENDUSER_ID, principal.getName());
		}
	}

	@Override
	protected String flavor(HttpServletRequest connection, HttpServletRequest request) {
		return connection.getProtocol();
	}

	@Override
	protected String requestHeader(HttpServletRequest httpServletRequest, String name) {
		return httpServletRequest.getHeader(name);
	}

	private  String getSpanName(HttpServletRequest request) {
		String spanName = request.getServletPath();
		if(spanName.equals("/services")){
			String servicename = request.getHeader("servicename");
			spanName = "API : "+servicename;
			this.parentContext = null;
		}else{
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
			spanName = contextPath + spanName;
		}
		if(spanName.equals("/PFSWeb/j_spring_security_check")){
			spanName = "Login Security Check";
			createParentContext(request, spanName);
		}else if(!isTraceRequired(request,spanName)){
			return null;
		}
		}
		return spanName;
	}
	
	private void createParentContext(HttpServletRequest request , String SpanName){
		Context context = null;
		context = (Context) request.getAttribute("eventContext");
		if(context == null){
			context = startSpan(request, request, SpanName);
		Scope scope = context.makeCurrent();
		request.setAttribute("eventContext", context);
		request.setAttribute("eventScope", scope);
		}
		this.parentContext = context;
	}
	
	private boolean isTraceRequired(HttpServletRequest request ,String spanName){
		if(spanName.contains("images") || spanName.contains("Charts") || request.getRequestURI().contains("/web")){
			return false;
		}
		createParentContext(request, spanName);
		return true;
	}
	
}
