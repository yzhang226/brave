package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

import java.io.IOException;

import javax.inject.Inject;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts incoming container requests and extracts any trace information from the request header
 * Also sends sr annotations.
 */
@Provider
@PreMatching
@Priority(0)
public class BraveContainerRequestFilter implements ContainerRequestFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder()} to customize. */
    public static BraveContainerRequestFilter create(Brave brave) {
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

        public BraveContainerRequestFilter build(Brave brave) {
            return new BraveContainerRequestFilter(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BraveContainerRequestFilter(Builder b, Brave brave) { // intentionally hidden
        this.requestInterceptor = brave.serverRequestInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(JaxRs2HttpServerRequest.class);
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerRequestAdapter.Factory<JaxRs2HttpServerRequest> requestAdapterFactory;

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    @Inject
    public BraveContainerRequestFilter(ServerRequestInterceptor interceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = interceptor;
        this.requestAdapterFactory = builder()
            .spanNameProvider(spanNameProvider)
            .requestFactoryBuilder.build(JaxRs2HttpServerRequest.class);
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        ServerRequestAdapter adapter =
            requestAdapterFactory.create(new JaxRs2HttpServerRequest(containerRequestContext));
        requestInterceptor.handle(adapter);
    }

}
