package com.github.kristofa.brave.http;


import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.KeyValueAnnotation;
import org.junit.Before;
import org.junit.Test;
import zipkin.TraceKeys;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class HttpClientResponseAdapterTest {

    private ClientResponseAdapter adapter;
    private HttpResponse response;

    @Before
    public void setup() {
        response = mock(HttpResponse.class);
        adapter = HttpClientResponseAdapter.factoryBuilder()
            .build(HttpResponse.class).create(response);
    }

    @Test
    public void successResponse() {
        when(response.getHttpStatusCode()).thenReturn(200);
        assertTrue(adapter.responseAnnotations().isEmpty());
        verify(response).getHttpStatusCode();
        verifyNoMoreInteractions(response);
    }

    @Test
    public void nonSuccessResponse() {
        when(response.getHttpStatusCode()).thenReturn(500);
        Collection<KeyValueAnnotation> annotations = adapter.responseAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals(TraceKeys.HTTP_STATUS_CODE, a.getKey());
        assertEquals("500", a.getValue());
    }
}
