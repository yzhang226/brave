package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client response interceptor.
 */
public class BraveHttpResponseInterceptor implements HttpResponseInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
    public static BraveHttpResponseInterceptor create(Brave brave) {
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

        public BraveHttpResponseInterceptor build(Brave brave) {
            return new BraveHttpResponseInterceptor(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BraveHttpResponseInterceptor(Builder b, Brave brave) { // intentionally hidden
        this.responseInterceptor = brave.clientResponseInterceptor();
        this.responseAdapterFactory = b.responseFactoryBuilder.build(HttpClientResponseImpl.class);
    }

    private final ClientResponseInterceptor responseInterceptor;
    private final ClientResponseAdapter.Factory<HttpClientResponseImpl> responseAdapterFactory;

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    public BraveHttpResponseInterceptor(final ClientResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
        this.responseAdapterFactory = builder().responseFactoryBuilder
            .build(HttpClientResponseImpl.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        ClientResponseAdapter adapter =
            responseAdapterFactory.create(new HttpClientResponseImpl(response));
        responseInterceptor.handle(adapter);
    }

}
