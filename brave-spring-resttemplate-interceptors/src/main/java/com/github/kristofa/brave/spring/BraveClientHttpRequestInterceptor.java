package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.NoAnnotationsClientResponseAdapter;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Spring {@link org.springframework.web.client.RestTemplate RestTemplate} {@link ClientHttpRequestInterceptor} that adds brave/zipkin annotations to outgoing client request and
 * logs the response.
 * <p/>
 * We assume the first part of the URI is the context path. The context name will be used as service name in endpoint.
 * Remaining part of path will be used as span name unless X-B3-SpanName http header is set. For example, if we have URI:
 * <p/>
 * <code>/service/path/a/b</code>
 * <p/>
 * The service name will be 'service'. The span name will be '/path/a/b'.
 * <p/>
 * For the response, it inspects the state. If the response indicates an error it submits error code and failure annotation. Finally it submits the client received annotation.
 */
public class BraveClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
    public static BraveClientHttpRequestInterceptor create(Brave brave) {
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

        public BraveClientHttpRequestInterceptor build(Brave brave) {
            return new BraveClientHttpRequestInterceptor(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BraveClientHttpRequestInterceptor(Builder b, Brave brave) { // intentionally hidden
        this.requestInterceptor = brave.clientRequestInterceptor();
        this.responseInterceptor = brave.clientResponseInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(SpringHttpClientRequest.class);
        this.responseAdapterFactory = b.responseFactoryBuilder.build(SpringHttpResponse.class);
    }

    private final ClientRequestInterceptor requestInterceptor;
    private final ClientResponseInterceptor responseInterceptor;
    private final ClientRequestAdapter.Factory<SpringHttpClientRequest> requestAdapterFactory;
    private final ClientResponseAdapter.Factory<SpringHttpResponse> responseAdapterFactory;

    /**
     * Creates a new instance.
     *
     * @param spanNameProvider Provides span name.
     * @param requestInterceptor Client request interceptor.
     * @param responseInterceptor Client response interceptor.
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    public BraveClientHttpRequestInterceptor(final ClientRequestInterceptor requestInterceptor, final ClientResponseInterceptor responseInterceptor,
                                             final SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        Builder builder = builder().spanNameProvider(spanNameProvider);
        this.requestAdapterFactory = builder.requestFactoryBuilder.build(SpringHttpClientRequest.class);
        this.responseAdapterFactory = builder.responseFactoryBuilder.build(SpringHttpResponse.class);
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution) throws IOException {
        ClientRequestAdapter requestAdapter =
            requestAdapterFactory.create(new SpringHttpClientRequest(request));
        requestInterceptor.handle(requestAdapter);

        final ClientHttpResponse response;

        try {
            response = execution.execute(request, body);
        } catch (RuntimeException | IOException up) {
            // Something went serious wrong communicating with the server; let the exception blow up
            responseInterceptor.handle(NoAnnotationsClientResponseAdapter.getInstance());

            throw up;
        }

        try {
            ClientResponseAdapter responseAdapter =
                responseAdapterFactory.create(new SpringHttpResponse(response.getRawStatusCode()));
            responseInterceptor.handle(responseAdapter);
        } catch (RuntimeException | IOException up) {
            // Ignore the failure of not being able to get the status code from the response; let the calling code find out themselves
            responseInterceptor.handle(NoAnnotationsClientResponseAdapter.getInstance());
        }

        return response;
    }
}
