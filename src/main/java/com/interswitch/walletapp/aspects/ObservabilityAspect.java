package com.interswitch.walletapp.aspects;

import com.interswitch.walletapp.annotation.ObserveParam;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class ObservabilityAspect {

    @Around("execution(* interswitch.walletapp.*.*(..)) ")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {

        String method = pjp.getSignature().getName();
        String context = resolveContext(pjp);

        String traceId = MDC.get("traceId");
        boolean addedLocally = false;

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            addedLocally = true;
            MDC.put("traceId", traceId);
        }


        String spanId = UUID.randomUUID().toString();
        MDC.put("spanId", spanId);

        log.info("→ {}{}", method, context);
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("← {} completed in {}ms{}", method, System.currentTimeMillis() - start, context);
            return result;
        } catch (Exception ex) {
            log.error("← {} failed in {}ms{} — {}", method, System.currentTimeMillis() - start, context, ex.getMessage());
            throw ex;
        } finally {
            MDC.remove("traceId");
            if (addedLocally) MDC.remove("traceId");
        }
    }

    private String resolveContext(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Parameter[] params = sig.getMethod().getParameters();
        Object[] args = pjp.getArgs();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            ObserveParam annotation = params[i].getAnnotation(ObserveParam.class);
            if (annotation != null) {
                sb.append(" [").append(annotation.value()).append("=").append(args[i]).append("]");
            }
        }
        return sb.toString();
    }
}