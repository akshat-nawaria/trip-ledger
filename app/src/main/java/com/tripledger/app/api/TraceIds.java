package com.tripledger.app.api;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * A short random id per request. It goes into every log line of that request (via the logging MDC),
 * into the X-Trace-Id response header, and into every error body. So when someone reports
 * "I got an error, trace 3f9a1c...", we can find exactly that request in the logs - without the
 * error message itself ever having to reveal anything.
 */
public final class TraceIds {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceIds() {
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // "-" only shows up if something runs outside a web request (shouldn't happen for API errors)
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "-" : id;
    }
}
