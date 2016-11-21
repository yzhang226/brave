package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client request interceptor.
 */
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
    public static BraveHttpRequestInterceptor create(Brave brave) {
        return new Builder().build(brave);
    }

    public static BraveHttpRequestInterceptor.Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<BraveHttpRequestInterceptor.Builder> {
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

        public BraveHttpRequestInterceptor build(Brave brave) {
            return new BraveHttpRequestInterceptor(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BraveHttpRequestInterceptor(Builder b, Brave brave) { // intentionally hidden
        this.requestInterceptor = brave.clientRequestInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(HttpClientRequestImpl.class);
    }

    private final ClientRequestInterceptor requestInterceptor;
    private final ClientRequestAdapter.Factory<HttpClientRequestImpl> requestAdapterFactory;

    /**
     * Creates a new instance.
     *
     * @param requestInterceptor
     * @param spanNameProvider Provides span name for request.
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    public BraveHttpRequestInterceptor(ClientRequestInterceptor requestInterceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.requestAdapterFactory = builder().spanNameProvider(spanNameProvider)
            .requestFactoryBuilder.build(HttpClientRequestImpl.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context) {
        ClientRequestAdapter adapter =
            requestAdapterFactory.create(new HttpClientRequestImpl(request));
        requestInterceptor.handle(adapter);
    }
}
