package com.github.kristofa.brave.http;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import java.net.URI;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import zipkin.TraceKeys;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpServerRequestAdapterTest {

    private final static String TRACE_ID = "7a842183262a6c62";
    private final static String SPAN_ID = "bf38b90488a1e481";
    private final static String PARENT_SPAN_ID = "8000000000000000";

    private ServerRequestAdapter adapter;
    private HttpServerRequest serverRequest;
    private SpanNameProvider spanNameProvider;

    @Before
    public void setup() {
        serverRequest = mock(HttpServerRequest.class);
        spanNameProvider = mock(SpanNameProvider.class);
        adapter = HttpServerRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider).build(HttpServerRequest.class)
            .create(serverRequest);
    }

    @Test
    public void getTraceDataNoSampledHeader() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn(null);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertNull(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledFalse() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("false");
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertFalse(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledFalseUpperCase() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("FALSE");
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertFalse(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    /**
     * This is according to the zipkin 'spec'.
     */
    @Test
    public void getTraceDataSampledZero() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("0");
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertFalse(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledTrueNoOtherTraceHeaders() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("true");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(null);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(null);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertNull(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledTrueNoParentId() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("true");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertNull(spanId.nullableParentId());
    }

    /**
     * This is according to the zipkin 'spec'.
     */
    @Test
    public void getTraceDataSampledOneNoParentId() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("1");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertNull(spanId.nullableParentId());
    }

    @Test
    public void supports128BitTraceIdHeader() {
        String upper64Bits = "48485a3953bb6124";
        String lower64Bits = "48485a3953bb6124";
        String hex128Bits = upper64Bits + lower64Bits;
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("1");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(hex128Bits);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(lower64Bits);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(upper64Bits), spanId.traceIdHigh);
        assertEquals(IdConversion.convertToLong(lower64Bits), spanId.traceId);
        assertEquals(IdConversion.convertToLong(lower64Bits), spanId.spanId);
        assertNull(spanId.nullableParentId());
    }

    @Test
    public void getTraceDataSampledTrueWithParentId() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("true");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.ParentSpanId.getName())).thenReturn(PARENT_SPAN_ID);

        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertEquals(IdConversion.convertToLong(PARENT_SPAN_ID), spanId.parentId);
    }

    @Test
    public void fullUriAnnotation() throws Exception {
        when(serverRequest.getUri()).thenReturn(new URI("http://youruri.com/a/b?myquery=you"));
        Collection<KeyValueAnnotation> annotations = adapter.requestAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals(TraceKeys.HTTP_URL, a.getKey());
        assertEquals("http://youruri.com/a/b?myquery=you", a.getValue());
    }

    /**
     * When the caller propagates IDs, but not a sampling decision, the local process should decide.
     */
    @Test
    public void getTraceData_externallyProvidedIds() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertNull(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertNull(spanId.nullableParentId());
    }
}
