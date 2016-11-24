package com.github.kristofa.brave;

import java.util.Collection;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;

/**
 * Adapter used to get tracing information from and add tracing information to a new request.
 *
 */
public interface ClientRequestAdapter {

    interface Factory<R> {
        ClientRequestAdapter create(R request);
    }

    /**
     * Gets the span name for request.
     *
     * @return Span name for request.
     */
    String getSpanName();

    /**
     * Enrich the request with the Spanid so we pass the state to the
     * service we are calling.
     *
     * @param spanId Nullable span id. If null we don't need to trace request and you
     *               should pass an indication along with the request that indicates we won't trace this request.
     */
    void addSpanIdToRequest(@Nullable SpanId spanId);

    /**
     * Returns a collection of annotations that should be added to span
     * for given request.
     *
     * Can be used to indicate more details about request next to span name.
     * For example for http requests an annotation containing the uri path could be added.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> requestAnnotations();

    /**
     * Provides the remote server address information for additional tracking.
     *
     * Can be useful when communicating with non-traced services by adding server address to span
     * i.e. {@link zipkin.Constants#SERVER_ADDR}
     *
     * @return request's target server endpoint information
     */
    @Nullable
    Endpoint serverAddress();
}
