package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts JAX-RS 2 client requests and adds or forwards tracing information in the header.
 * Also sends cs annotations.
 */
@Provider
@Priority(0)
public class BraveClientRequestFilter implements ClientRequestFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder()} to customize. */
    public static BraveClientRequestFilter create(Brave brave) {
        return new Builder().build(brave);
    }

    public static BraveClientRequestFilter.Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<BraveClientRequestFilter.Builder> {
        final HttpClientRequestAdapter.FactoryBuilder requestFactoryBuilder
            = HttpClientRequestAdapter.factoryBuilder();

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

        public BraveClientRequestFilter build(Brave brave) {
            return new BraveClientRequestFilter(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BraveClientRequestFilter(Builder b, Brave brave) { // intentionally hidden
        this.requestInterceptor = brave.clientRequestInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(JaxRs2HttpClientRequest.class);
    }

    private final ClientRequestInterceptor requestInterceptor;
    private final ClientRequestAdapter.Factory<JaxRs2HttpClientRequest> requestAdapterFactory;

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    @Inject
    public BraveClientRequestFilter(SpanNameProvider spanNameProvider, ClientRequestInterceptor requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
        this.requestAdapterFactory = HttpClientRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider)
            .build(JaxRs2HttpClientRequest.class);
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        ClientRequestAdapter adapter =
            requestAdapterFactory.create(new JaxRs2HttpClientRequest(clientRequestContext));
        requestInterceptor.handle(adapter);
    }
}
