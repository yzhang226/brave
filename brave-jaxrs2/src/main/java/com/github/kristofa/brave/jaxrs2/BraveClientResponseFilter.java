package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts JAX-RS 2 client responses and sends cr annotations. Also submits the completed span.
 */
@Provider
@Priority(0)
public class BraveClientResponseFilter implements ClientResponseFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder()} to customize. */
    public static BraveClientResponseFilter create(Brave brave) {
        return new Builder().build(brave);
    }

    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<Builder> {
        final HttpClientResponseAdapter.FactoryBuilder responseFactoryBuilder
            = HttpClientResponseAdapter.factoryBuilder();

        @Override public Builder addKey(String key) {
            responseFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            responseFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public BraveClientResponseFilter build(Brave brave) {
            return new BraveClientResponseFilter(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BraveClientResponseFilter(Builder b, Brave brave) { // intentionally hidden
        this.responseInterceptor = brave.clientResponseInterceptor();
        this.responseAdapterFactory = b.responseFactoryBuilder.build(JaxRs2HttpResponse.class);
    }

    private final ClientResponseInterceptor responseInterceptor;
    private final ClientResponseAdapter.Factory<JaxRs2HttpResponse> responseAdapterFactory;

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    @Inject
    public BraveClientResponseFilter(ClientResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
        this.responseAdapterFactory = builder().responseFactoryBuilder.build(JaxRs2HttpResponse.class);
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {
        ClientResponseAdapter adapter =
            responseAdapterFactory.create(new JaxRs2HttpResponse(clientResponseContext));
        responseInterceptor.handle(adapter);
    }
}
