package com.github.kristofa.brave.okhttp;


import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public class BraveOkHttpRequestResponseInterceptor implements Interceptor {

  /** Creates a tracing interceptor with defaults. Use {@link #builder()} to customize. */
  public static BraveOkHttpRequestResponseInterceptor create(Brave brave) {
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

    public BraveOkHttpRequestResponseInterceptor build(Brave brave) {
      return new BraveOkHttpRequestResponseInterceptor(this, checkNotNull(brave, "brave"));
    }

    Builder() { // intentionally hidden
    }
  }

  BraveOkHttpRequestResponseInterceptor(Builder b, Brave brave) { // intentionally hidden
    this.requestInterceptor = brave.clientRequestInterceptor();
    this.responseInterceptor = brave.clientResponseInterceptor();
    this.requestAdapterFactory = b.requestFactoryBuilder.build(OkHttpRequest.class);
    this.responseAdapterFactory = b.responseFactoryBuilder.build(OkHttpResponse.class);
  }

  private final ClientRequestInterceptor requestInterceptor;
  private final ClientResponseInterceptor responseInterceptor;
  private final ClientRequestAdapter.Factory<OkHttpRequest> requestAdapterFactory;
  private final ClientResponseAdapter.Factory<OkHttpResponse> responseAdapterFactory;

  /**
   * @deprecated please use {@link #create(Brave)} or {@link #builder()}
   */
  @Deprecated
  public BraveOkHttpRequestResponseInterceptor(ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider) {
    this.requestInterceptor = requestInterceptor;
    this.responseInterceptor = responseInterceptor;
    Builder builder = builder().spanNameProvider(spanNameProvider);
    this.requestAdapterFactory = builder.requestFactoryBuilder.build(OkHttpRequest.class);
    this.responseAdapterFactory = builder.responseFactoryBuilder.build(OkHttpResponse.class);
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    Request.Builder builder = request.newBuilder();
    ClientRequestAdapter requestAdapter =
        requestAdapterFactory.create(new OkHttpRequest(builder, request));
    requestInterceptor.handle(requestAdapter);
    Response response = chain.proceed(builder.build());
    ClientResponseAdapter responseAdapter =
        responseAdapterFactory.create(new OkHttpResponse(response));
    responseInterceptor.handle(responseAdapter);
    return response;
  }

}
