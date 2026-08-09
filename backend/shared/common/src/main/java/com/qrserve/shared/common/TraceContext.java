package com.qrserve.shared.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility for generating and propagating distributed tracing context
 * (traceId / spanId) across service boundaries.
 *
 * <p>Values are stored in the SLF4J MDC so that log lines emitted during
 * the current request carry the same trace identifiers. When Spring Cloud
 * Sleuth or OpenTelemetry is added to the project, the MDC keys
 * "traceId" and "spanId" will be populated automatically and this class
 * will simply read them.</p>
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";

    private TraceContext() {
    }

    /**
     * Returns the current traceId from MDC, or generates a new one if absent.
     */
    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        // traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * Returns the current spanId from MDC, or generates a new one if absent.
     */
    public static String getSpanId() {
        String spanId = MDC.get(SPAN_ID_KEY);
        if (spanId == null || spanId.isBlank()) {
            spanId = generateSpanId();
            MDC.put(SPAN_ID_KEY, spanId);
        }
        return spanId;
    }

    /**
     * Sets a new trace/span pair in the MDC.
     */
    public static void setContext(String traceId, String spanId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
        if (spanId != null && !spanId.isBlank()) {
            MDC.put(SPAN_ID_KEY, spanId);
        }
    }

    /**
     * Clears the trace context from the MDC.
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String generateSpanId() {
        return UUID.randomUUID().toString().substring(0, 16).replace("-", "");
    }
}
