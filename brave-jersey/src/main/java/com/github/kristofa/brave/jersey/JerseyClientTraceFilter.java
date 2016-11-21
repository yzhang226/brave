package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * This filter creates or forwards trace headers and sends cs and cr annotations. Usage:
 *
 * <pre>
 * Client client = Client.create()
 * client.addFilter(JerseyClientTraceFilter.create(brave));
 * </pre>
 */
@Singleton
public class JerseyClientTraceFilter extends ClientFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder()} to customize. */
    public static JerseyClientTraceFilter create(Brave brave) {
        return new Builder().build(brave);
    }

    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<Builder> {
        final HttpClientRequestAdapter.FactoryBuilder requestFactoryBuilder
            = HttpClientRequestAdapter.factoryBuilder();
        final HttpClientResponseAdapter.FactoryBuilder responseFactoryBuilder
            = HttpClientResponseAdapter.factoryBuilder();

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

        public JerseyClientTraceFilter build(Brave brave) {
            return new JerseyClientTraceFilter(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    JerseyClientTraceFilter(Builder b, Brave brave) { // intentionally hidden
        this.clientRequestInterceptor = brave.clientRequestInterceptor();
        this.clientResponseInterceptor = brave.clientResponseInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(JerseyHttpRequest.class);
        this.responseAdapterFactory = b.responseFactoryBuilder.build(JerseyHttpResponse.class);
    }

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final ClientRequestAdapter.Factory<JerseyHttpRequest> requestAdapterFactory;
    private final ClientResponseAdapter.Factory<JerseyHttpResponse> responseAdapterFactory;

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    @Inject
    public JerseyClientTraceFilter(SpanNameProvider spanNameProvider, ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor) {
        this.clientRequestInterceptor = requestInterceptor;
        this.clientResponseInterceptor = responseInterceptor;
        Builder builder = builder().spanNameProvider(spanNameProvider);
        this.requestAdapterFactory = builder.requestFactoryBuilder.build(JerseyHttpRequest.class);
        this.responseAdapterFactory = builder.responseFactoryBuilder.build(JerseyHttpResponse.class);
    }

    @Override
    public ClientResponse handle(final ClientRequest clientRequest) throws ClientHandlerException {
        ClientRequestAdapter requestAdapter =
            requestAdapterFactory.create(new JerseyHttpRequest(clientRequest));
        clientRequestInterceptor.handle(requestAdapter);
        final ClientResponse clientResponse = getNext().handle(clientRequest);
        ClientResponseAdapter responseAdapter =
            responseAdapterFactory.create(new JerseyHttpResponse(clientResponse));
        clientResponseInterceptor.handle(responseAdapter);
        return clientResponse;
    }
}
