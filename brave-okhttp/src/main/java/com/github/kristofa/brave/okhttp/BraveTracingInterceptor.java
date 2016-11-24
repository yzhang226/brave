package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.TagExtractor.ValueParserFactory;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.github.kristofa.brave.internal.TagExtractorBuilder;
import com.twitter.zipkin.gen.Endpoint;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.Constants;
import zipkin.TraceKeys;

import static com.github.kristofa.brave.IdConversion.convertToString;
import static com.github.kristofa.brave.http.BraveHttpHeaders.Sampled;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * This is an OkHttp interceptor which traces client requests. It should be applied as both an
 * {@link OkHttpClient#interceptors() application interceptor} and as a {@link
 * OkHttpClient#networkInterceptors() network interceptor}.
 *
 * <p>The implementation models the application request as a local span. Each network request will
 * be a child span. For example, if there's a redirect, there will be one for the application
 * request and two spans for the associated network requests.
 *
 * Trace identifiers of each network attempt are propagated to the server via headers prefixed with
 * `X-B3`. These spans are also reported out of band, with {@link zipkin.Constants#CLIENT_SEND} and
 * {@link zipkin.Constants#CLIENT_RECV} annotations, binary annotations (tags) like {@link
 * TraceKeys#HTTP_URL} and {@link zipkin.Constants#SERVER_ADDR the server's ip and port}.
 *
 * <h3>Configuration</h3>
 *
 * <p>Since this interceptor creates nested spans, you should use nesting-aware
 * span state like {@link InheritableServerClientAndLocalSpanState}. If using
 * asynchronous calls, you must also wrap the dispatcher's executor
 * service. Regardless, the interceptor must be registered as both an
 * application and network interceptor.
 *
 * <p>Here's how to add tracing to OkHttp:
 * <pre>{@code
 * brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))..
 *
 * // The request dispatcher uses an executor service.. wrap it!
 * tracePropagatingExecutor = new BraveExecutorService(
 *   new Dispatcher().executorService(),
 *   brave.serverSpanThreadBinder()
 * );
 *
 * client = new OkHttpClient.Builder()
 *   .addInterceptor(tracingInterceptor)
 *   .addNetworkInterceptor(tracingInterceptor)
 *   .dispatcher(new Dispatcher(tracePropagatingExecutor));
 *   .build();
 * }</pre>
 */
public final class BraveTracingInterceptor implements Interceptor {
  public static BraveTracingInterceptor create(Brave brave) {
    return builder(brave).build();
  }

  /** Defaults to use {@link OkHttpParser} */
  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder implements TagExtractor.Config<Builder> {
    final Brave brave;
    final TagExtractorBuilder tagExtractorBuilder;

    String serverName = "";
    OkHttpParser parser = new OkHttpParser();

    Builder(Brave brave) {
      this.brave = checkNotNull(brave, "brave");
      this.tagExtractorBuilder = TagExtractorBuilder.create();
      this.tagExtractorBuilder.addValueParserFactory(new OkHttpValueParserFactory());
    }

    @Override public Builder addKey(String key) {
      tagExtractorBuilder.addKey(key);
      return this;
    }

    @Override public Builder addValueParserFactory(ValueParserFactory factory) {
      tagExtractorBuilder.addValueParserFactory(factory);
      return this;
    }

    /**
     * Indicates the service name used for the {@link Constants#SERVER_ADDR server address}.Default
     * is empty string.
     *
     * <p>Setting this is not important when the server is instrumented with Zipkin. This is
     * important when the server is not instrumented with Zipkin. For example, if you are calling a
     * cloud service, you will see this name as a leaf in your service dependency graph.
     */
    public Builder serverName(String serverName) {
      this.serverName = checkNotNull(serverName, "serverName");
      return this;
    }

    /** Controls the metadata recorded in spans representing http operations. */
    public Builder parser(OkHttpParser parser) {
      this.parser = checkNotNull(parser, "parser");
      return this;
    }

    public BraveTracingInterceptor build() {
      return new BraveTracingInterceptor(this);
    }
  }

  final LocalTracer localTracer;
  final ClientTracer clientTracer;
  final OkHttpParser parser;
  final String serverName;
  final TagExtractor<Request> requestTagExtractor;
  final TagExtractor<Response> responseTagExtractor;

  BraveTracingInterceptor(Builder builder) {
    localTracer = builder.brave.localTracer();
    clientTracer = builder.brave.clientTracer();
    parser = builder.parser;
    requestTagExtractor = builder.tagExtractorBuilder.build(Request.class);
    responseTagExtractor = builder.tagExtractorBuilder.build(Response.class);
    serverName = builder.serverName;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Connection connection = chain.connection();
    boolean applicationRequest = connection == null;

    Request request = chain.request();
    SpanId spanId;
    if (applicationRequest) {
      spanId = localTracer.startNewSpan("okhttp", parser.applicationSpanName(request));
    } else {
      spanId = clientTracer.startNewSpan(parser.networkSpanName(request));
    }

    if (spanId == null) { // trace was unsampled
      return applicationRequest
          ? chain.proceed(request)
          : chain.proceed(request.newBuilder().header(Sampled.getName(), "0").build());
    } else if (applicationRequest) {
      return traceApplicationRequest(chain, request);
    } else {
      Request tracedRequest = addTraceHeaders(request, spanId).build();
      return traceNetworkRequest(chain, tracedRequest);
    }
  }

  static Request.Builder addTraceHeaders(Request request, SpanId spanId) {
    Request.Builder tracedRequest = request.newBuilder();
    tracedRequest.header(BraveHttpHeaders.TraceId.getName(), spanId.traceIdString());
    tracedRequest.header(BraveHttpHeaders.SpanId.getName(), convertToString(spanId.spanId));
    if (spanId.nullableParentId() != null) {
      tracedRequest.header(BraveHttpHeaders.ParentSpanId.getName(),
          convertToString(spanId.parentId));
    }
    tracedRequest.header(BraveHttpHeaders.Sampled.getName(), "1");
    return tracedRequest;
  }

  /** We do not add trace headers to the application request, as it never leaves the process */
  Response traceApplicationRequest(Chain chain, Request request) throws IOException {
    try {
      return chain.proceed(request);
    } catch (IOException | RuntimeException | Error e) {
      // TODO: revisit https://github.com/openzipkin/openzipkin.github.io/issues/52
      localTracer.submitBinaryAnnotation(Constants.ERROR, e.getMessage());
      throw e;
    } finally {
      localTracer.finishSpan(); // span must be closed!
    }
  }

  Response traceNetworkRequest(Chain chain, Request request) throws IOException {
    appendToSpan(requestTagExtractor.extractTags(request));
    appendToSpan(parser.networkRequestTags(request));
    try {
      clientTracer.setClientSent(serverAddress(chain.connection()));
      Response response = chain.proceed(request);
      appendToSpan(parser.networkResponseTags(response));
      appendToSpan(responseTagExtractor.extractTags(response));
      return response;
    } catch (IOException | RuntimeException | Error e) {
      // TODO: revisit https://github.com/openzipkin/openzipkin.github.io/issues/52
      clientTracer.submitAnnotation(Constants.ERROR);
      throw e;
    } finally {
      clientTracer.setClientReceived(); // span must be closed!
    }
  }

  void appendToSpan(Collection<KeyValueAnnotation> tags) {
    for (KeyValueAnnotation tag : tags) {
      clientTracer.submitBinaryAnnotation(tag.getKey(), tag.getValue());
    }
  }

  Endpoint serverAddress(Connection connection) {
    InetSocketAddress sa = connection.route().socketAddress();
    Endpoint.Builder builder = Endpoint.builder().serviceName(serverName).port(sa.getPort());
    byte[] address = sa.getAddress().getAddress();
    if (address.length == 4) {
      builder.ipv4(ByteBuffer.wrap(address).getInt());
    } else if (address.length == 16) {
      builder.ipv6(address);
    }
    return builder.build();
  }
}
