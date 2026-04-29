package com.interswitch.walletapp.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String TRACE_ID_ATTR = "traceIdAddedLocally";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String traceId = MDC.get("traceId");
        boolean addedLocally = false;

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            addedLocally = true;
            MDC.put("traceId", traceId);
        }

        String spanId = UUID.randomUUID().toString();
        MDC.put("spanId", spanId);

        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(TRACE_ID_ATTR, addedLocally);

        String method = request.getMethod();
        String uri = request.getRequestURI();

        log.info("→ {} {}", method, uri);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        if (ex != null) {
            log.error("← {} {} [{}] failed in {}ms — {}", method, uri, status, duration, ex.getMessage());
        } else {
            log.info("← {} {} [{}] completed in {}ms", method, uri, status, duration);
        }

        Boolean addedLocally = (Boolean) request.getAttribute(TRACE_ID_ATTR);
        if (Boolean.TRUE.equals(addedLocally)) {
            MDC.remove("traceId");
        }
        MDC.remove("spanId");
    }
}
