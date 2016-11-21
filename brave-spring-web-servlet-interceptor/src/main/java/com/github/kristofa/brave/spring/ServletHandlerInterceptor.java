package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

@Configuration
public class ServletHandlerInterceptor extends HandlerInterceptorAdapter {

    @Bean ServletHandlerInterceptor create(Brave brave, SpanNameProvider spanNameProvider) {
        return builder().spanNameProvider(spanNameProvider).build(brave);
    }

    static final String HTTP_SERVER_SPAN_ATTRIBUTE = ServletHandlerInterceptor.class.getName() + ".server-span";

    /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
    public static ServletHandlerInterceptor create(Brave brave) {
        return new Builder().build(brave);
    }

    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<Builder> {
        final HttpServerRequestAdapter.FactoryBuilder requestFactoryBuilder
                = HttpServerRequestAdapter.factoryBuilder();
        final HttpServerResponseAdapter.FactoryBuilder responseFactoryBuilder
                = HttpServerResponseAdapter.factoryBuilder();

        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            requestFactoryBuilder.spanNameProvider(spanNameProvider);
            return this;
        }

        @Override public Builder addKey(String key) {
            requestFactoryBuilder.addKey(key);
            responseFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            requestFactoryBuilder.addValueParserFactory(factory);
            responseFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public ServletHandlerInterceptor build(Brave brave) {
            return new ServletHandlerInterceptor(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    ServletHandlerInterceptor(Builder b, Brave brave) { // intentionally hidden
        this.requestInterceptor = brave.serverRequestInterceptor();
        this.responseInterceptor = brave.serverResponseInterceptor();
        this.serverThreadBinder = brave.serverSpanThreadBinder();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(HttpServerRequest.class);
        this.responseAdapterFactory = b.responseFactoryBuilder.build(HttpResponse.class);
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final ServerSpanThreadBinder serverThreadBinder;
    private final ServerRequestAdapter.Factory<HttpServerRequest> requestAdapterFactory;
    private final ServerResponseAdapter.Factory<HttpResponse> responseAdapterFactory;

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    public ServletHandlerInterceptor(ServerRequestInterceptor requestInterceptor, ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider, final ServerSpanThreadBinder serverThreadBinder) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.serverThreadBinder = serverThreadBinder;
        Builder builder = builder().spanNameProvider(spanNameProvider);
        this.requestAdapterFactory = builder.requestFactoryBuilder.build(HttpServerRequest.class);
        this.responseAdapterFactory = builder.responseFactoryBuilder.build(HttpResponse.class);
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        ServerRequestAdapter adapter = requestAdapterFactory.create(new HttpServerRequest() {
                @Override
                public String getHttpHeaderValue(String headerName) {
                    return request.getHeader(headerName);
                }

                @Override
                public URI getUri() {
                    try {
                        return new URI(request.getRequestURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public String getHttpMethod() {
                    return request.getMethod();
                }
            });
        requestInterceptor.handle(adapter);

        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        request.setAttribute(HTTP_SERVER_SPAN_ATTRIBUTE, serverThreadBinder.getCurrentServerSpan());
        serverThreadBinder.setCurrentSpan(null);
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) {

        final ServerSpan span = (ServerSpan) request.getAttribute(HTTP_SERVER_SPAN_ATTRIBUTE);

        if (span != null) {
            serverThreadBinder.setCurrentSpan(span);
        }

        ServerResponseAdapter adapter = responseAdapterFactory.create(new HttpResponse() {
              @Override
              public int getHttpStatusCode() {
                return response.getStatus();
              }
            });
        responseInterceptor.handle(adapter);
    }

}
