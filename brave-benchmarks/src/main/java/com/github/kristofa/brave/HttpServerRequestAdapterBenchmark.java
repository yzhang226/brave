package com.github.kristofa.brave;

import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
public class HttpServerRequestAdapterBenchmark {

    @State(Scope.Benchmark)
    public static class Data {
        final URI URI = java.net.URI.create("http://localhost");
        final Random random = new Random(42);
        final HttpServerRequest request = new HttpServerRequest() {
            @Override
            public String getHttpHeaderValue(String headerName) {
                if (BraveHttpHeaders.Sampled.getName().equals(headerName)) {
                    switch (random.nextInt(6)) {
                        case 0:
                            return null;
                        case 1:
                            return "1";
                        case 2:
                            return "true";
                        case 3:
                            return "false";
                        case 4:
                            return "False";
                        case 5:
                            return "FALSE";
                    }
                }
                if (BraveHttpHeaders.TraceId.getName().equals(headerName)
                        || BraveHttpHeaders.SpanId.getName().equals(headerName)
                        || BraveHttpHeaders.ParentSpanId.getName().equals(headerName)) {
                    return "1234";
                }
                return null;
            }

            @Override
            public URI getUri() {
                return URI;
            }

            @Override
            public String getHttpMethod() {
                return "GET";
            }
        };

        final ServerRequestAdapter adapter = HttpServerRequestAdapter.factoryBuilder()
            .spanNameProvider(r -> r.getHttpMethod() + " " + r.getUri().getPath())
            .build(HttpServerRequest.class).create(request);
    }

    @Benchmark
    public TraceData httpServerRequestAdapter(Data data) {
        return data.adapter.getTraceData();
    }

    // Convenience main entry-point
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + HttpServerRequestAdapterBenchmark.class.getSimpleName() + ".*")
                .build();

        new Runner(opt).run();
    }
}
