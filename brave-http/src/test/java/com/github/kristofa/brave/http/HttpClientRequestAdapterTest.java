package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import java.net.URI;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import zipkin.TraceKeys;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class HttpClientRequestAdapterTest {

    private static final String SPAN_NAME = "span_name";
    private static final long TRACE_ID = 1;
    private static final long SPAN_ID = 2;
    private static final Long PARENT_SPAN_ID = 3L;
    private static final String TEST_URI = "http://abc.com/request";

    private ClientRequestAdapter clientRequestAdapter;
    private HttpClientRequest request;
    private SpanNameProvider spanNameProvider;

    @Before
    public void setup() {
        request = mock(HttpClientRequest.class);
        spanNameProvider = mock(SpanNameProvider.class);
        clientRequestAdapter = HttpClientRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider)
            .build(HttpClientRequest.class)
            .create(request);
    }

    @Test
    public void getSpanName() {
        when(spanNameProvider.spanName(request)).thenReturn(SPAN_NAME);
        assertEquals(SPAN_NAME, clientRequestAdapter.getSpanName());
        verify(spanNameProvider).spanName(request);
        verifyNoMoreInteractions(request, spanNameProvider);
    }

    @Test
    public void addSpanIdToRequest_NoSpanId() {
        clientRequestAdapter.addSpanIdToRequest(null);
        verify(request).addHeader(BraveHttpHeaders.Sampled.getName(), "0");
        verifyNoMoreInteractions(request, spanNameProvider);
    }

    @Test
    public void addSpanIdToRequest_WithParentSpanId() {
        SpanId id = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(PARENT_SPAN_ID).build();
        clientRequestAdapter.addSpanIdToRequest(id);
        verify(request).addHeader(BraveHttpHeaders.Sampled.getName(), "1");
        verify(request).addHeader(BraveHttpHeaders.TraceId.getName(), "0000000000000001");
        verify(request).addHeader(BraveHttpHeaders.SpanId.getName(), String.valueOf(SPAN_ID));
        verify(request).addHeader(BraveHttpHeaders.ParentSpanId.getName(), String.valueOf(PARENT_SPAN_ID));
        verifyNoMoreInteractions(request, spanNameProvider);
    }

    @Test
    public void addSpanIdToRequest_WithoutParentSpanId() {
        SpanId id = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();
        clientRequestAdapter.addSpanIdToRequest(id);
        verify(request).addHeader(BraveHttpHeaders.Sampled.getName(), "1");
        verify(request).addHeader(BraveHttpHeaders.TraceId.getName(), "0000000000000001");
        verify(request).addHeader(BraveHttpHeaders.SpanId.getName(), String.valueOf(SPAN_ID));
        verifyNoMoreInteractions(request, spanNameProvider);
    }

    @Test
    public void requestAnnotations() {
        when(request.getUri()).thenReturn(URI.create(TEST_URI));
        Collection<KeyValueAnnotation> annotations = clientRequestAdapter.requestAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals(TraceKeys.HTTP_URL, a.getKey());
        assertEquals(TEST_URI, a.getValue());
        verify(request).getUri();
        verifyNoMoreInteractions(request, spanNameProvider);
    }

    @Test
    public void traceId_when128bit() throws Exception {
        SpanId id = SpanId.builder().traceIdHigh(TRACE_ID).traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();
        clientRequestAdapter.addSpanIdToRequest(id);
        verify(request).addHeader(BraveHttpHeaders.Sampled.getName(), "1");
        verify(request).addHeader(BraveHttpHeaders.TraceId.getName(), "00000000000000010000000000000001");
        verify(request).addHeader(BraveHttpHeaders.SpanId.getName(), String.valueOf(SPAN_ID));
        verifyNoMoreInteractions(request, spanNameProvider);
    }
}
