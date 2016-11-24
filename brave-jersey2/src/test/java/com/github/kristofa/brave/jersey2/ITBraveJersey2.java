package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveClientRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveClientResponseFilter;
import com.twitter.zipkin.gen.Span;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ITBraveJersey2 extends JerseyTest {

    private Brave brave;
    private SpanNameProvider spanNameProvider;

    @Override
    protected Application configure() {
        ApplicationContext context = new AnnotationConfigApplicationContext(JerseyTestSpringConfig.class);
        brave = context.getBean(Brave.class);
        spanNameProvider = context.getBean(SpanNameProvider.class);
        return new JerseyTestConfig().property("contextConfig", context);
    }

    @Test
    public void testBraveJersey2() {
        WebTarget target = target("/brave-jersey2/test");
        target.register(BraveClientRequestFilter.builder().spanNameProvider(spanNameProvider).build(brave));
        target.register(BraveClientResponseFilter.create(brave));

        final Response response = target.request().get();
        assertEquals(200, response.getStatus());

        final List<Span> collectedSpans = SpanCollectorForTesting.getInstance().getCollectedSpans();
        assertEquals(2, collectedSpans.size());
        final Span clientSpan = collectedSpans.get(0);
        final Span serverSpan = collectedSpans.get(1);

        assertEquals("Expected trace id's to be equal", clientSpan.getTrace_id(), serverSpan.getTrace_id());
        assertEquals("Expected span id's to be equal", clientSpan.getId(), serverSpan.getId());
        assertEquals("Expected parent span id's to be equal", clientSpan.getParent_id(), serverSpan.getParent_id());
        assertEquals("Span names of client and server should be equal.", clientSpan.getName(), serverSpan.getName());
        assertEquals("Expect 2 annotations.", 2, clientSpan.getAnnotations().size());
        assertEquals("Expect 2 annotations.", 2, serverSpan.getAnnotations().size());
        assertEquals("service name of end points for both client and server annotations should be equal.",
            clientSpan.getAnnotations().get(0).host.service_name,
            serverSpan.getAnnotations().get(0).host.service_name
        );
    }
}
