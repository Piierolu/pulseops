package com.pulseops.controlplane.execution;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.LinkedHashMap;
import java.util.Map;

record TraceHeaders(String traceparent, String tracestate) {

    static TraceHeaders capture() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (!spanContext.isValid()) {
            return new TraceHeaders(null, null);
        }
        String traceparent = "00-%s-%s-%s".formatted(
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                spanContext.getTraceFlags().asHex()
        );
        String tracestate = spanContext.getTraceState().isEmpty()
                ? null
                : spanContext.getTraceState().asMap().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((left, right) -> left + "," + right)
                        .orElse(null);
        return new TraceHeaders(traceparent, tracestate);
    }

    Context restore() {
        if (traceparent == null) {
            return Context.root();
        }
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put("traceparent", traceparent);
        if (tracestate != null) {
            carrier.put("tracestate", tracestate);
        }
        return W3CTraceContextPropagator.getInstance().extract(Context.root(), carrier, MapGetter.INSTANCE);
    }

    private enum MapGetter implements TextMapGetter<Map<String, String>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }
}
