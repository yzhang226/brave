package com.github.kristofa.brave;

import java.util.Collection;

/**
 * Provides properties needed for dealing with server request.
 *
 * @see ServerRequestInterceptor
 */
public interface ServerRequestAdapter {

    interface Factory<R> {
        ServerRequestAdapter create(R request);
    }

    /**
     * Get the trace data from request.
     *
     * @return trace data.
     */
    TraceData getTraceData();

    /**
     * Gets the span name for request.
     *
     * @return Span name for request.
     */
    String getSpanName();

    /**
     * Returns a collection of annotations that should be added to span
     * for incoming request.
     *
     * Can be used to indicate more details about request next to span name.
     * For example for http requests an annotation containing the uri path could be added.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> requestAnnotations();
}
