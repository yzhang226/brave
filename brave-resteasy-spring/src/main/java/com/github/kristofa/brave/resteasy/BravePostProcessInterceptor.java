package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.TagExtractor;

import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.kristofa.brave.ServerTracer;
import org.springframework.stereotype.Component;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Rest Easy {@link PostProcessInterceptor} that will submit server send state.
 * 
 * @author kristof
 */
@Component
@Provider
@ServerInterceptor
public class BravePostProcessInterceptor implements PostProcessInterceptor {

    @Autowired
    BravePostProcessInterceptor(Brave brave) {
        this(builder(), brave);
    }

    /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
    public static BravePostProcessInterceptor create(Brave brave) {
        return new Builder().build(brave);
    }

    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder implements TagExtractor.Config<Builder> {
        final HttpServerResponseAdapter.FactoryBuilder responseFactoryBuilder
            = HttpServerResponseAdapter.factoryBuilder();

        @Override public Builder addKey(String key) {
            responseFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            responseFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public BravePostProcessInterceptor build(Brave brave) {
            return new BravePostProcessInterceptor(this, checkNotNull(brave, "brave"));
        }

        Builder() { // intentionally hidden
        }
    }

    BravePostProcessInterceptor(Builder b, Brave brave) { // intentionally hidden
        this.responseInterceptor = brave.serverResponseInterceptor();
        this.responseAdapterFactory = b.responseFactoryBuilder.build(HttpResponse.class);
    }

    private final ServerResponseInterceptor responseInterceptor;
    private final ServerResponseAdapter.Factory<HttpResponse> responseAdapterFactory;

    /**
     * Creates a new instance.
     * 
     * @param respInterceptor {@link ServerTracer}. Should not be null.
     * @deprecated please use {@link #create(Brave)} or {@link #builder()}
     */
    @Deprecated
    public BravePostProcessInterceptor(ServerResponseInterceptor respInterceptor) {
        this.responseInterceptor = respInterceptor;
        this.responseAdapterFactory = builder().responseFactoryBuilder.build(HttpResponse.class);
    }

    @Override
    public void postProcess(final ServerResponse response) {

        HttpResponse httpResponse = new HttpResponse() {

            @Override
            public int getHttpStatusCode() {
                return response.getStatus();
            }
        };
        ServerResponseAdapter adapter = responseAdapterFactory.create(httpResponse);
        responseInterceptor.handle(adapter);
    }

}
