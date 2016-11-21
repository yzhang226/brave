package com.github.kristofa.brave.resteasy;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Rest Easy {@link PreProcessInterceptor} that will:
 * <ol>
 * <li>Get trace data (trace id, span id, parent span id) from http headers and initialize state for request + submit 'server
 * received' for request.</li>
 * <li>If no trace information is submitted we will start a new span. In that case it means client does not support tracing
 * and should be adapted.</li>
 * </ol>
 *
 * @author kristof
 */
@Component
@Provider
@ServerInterceptor
public class BravePreProcessInterceptor implements PreProcessInterceptor {

    @Autowired // internal
    BravePreProcessInterceptor(SpanNameProvider spanNameProvider, Brave brave) {
        this(builder().spanNameProvider(spanNameProvider), brave);
    }

    /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
    public static BravePreProcessInterceptor create(Brave brave) {
        return new Builder().build(brave);
    }

    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<Builder> {
        final HttpServerRequestAdapter.FactoryBuilder requestFactoryBuilder
            = HttpServerRequestAdapter.factoryBuilder();

        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            requestFactoryBuilder.spanNameProvider(spanNameProvider);
            return this;
        }

        @Override public Builder addKey(String key) {
            requestFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            requestFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public BravePreProcessInterceptor build(Brave brave) {
            return new BravePreProcessInterceptor(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerRequestAdapter.Factory<RestEasyHttpServerRequest> requestAdapterFactory;

    @Context
    HttpServletRequest servletRequest;

    BravePreProcessInterceptor(Builder b, Brave brave) { // intentionally hidden
        this.requestInterceptor = brave.serverRequestInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(RestEasyHttpServerRequest.class);
    }

    /**
     * Creates a new instance.
     *
     * @param requestInterceptor Request interceptor.
     * @param spanNameProvider Span name provider.
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    public BravePreProcessInterceptor(ServerRequestInterceptor requestInterceptor,
                                      SpanNameProvider spanNameProvider
    ) {
        this.requestInterceptor = requestInterceptor;
        this.requestAdapterFactory = builder()
            .spanNameProvider(spanNameProvider)
            .requestFactoryBuilder.build(RestEasyHttpServerRequest.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerResponse preProcess(final HttpRequest request, final ResourceMethod method) throws Failure,
        WebApplicationException {
        ServerRequestAdapter adapter =
            requestAdapterFactory.create(new RestEasyHttpServerRequest(request));
        requestInterceptor.handle(adapter);
        return null;
    }

}
