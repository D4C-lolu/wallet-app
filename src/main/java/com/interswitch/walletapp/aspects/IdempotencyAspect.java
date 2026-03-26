package com.interswitch.walletapp.aspects;

import com.interswitch.walletapp.annotation.DisableFraudDetection;
import com.interswitch.walletapp.annotation.Idempotent;
import com.interswitch.walletapp.context.IdempotencyStore;
import com.interswitch.walletapp.exceptions.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyStore idempotencyStore;
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    // Tracks keys currently being processed by the current thread
    private static final ThreadLocal<Set<String>> PROCESSING_KEYS = ThreadLocal.withInitial(HashSet::new);

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        // If not a web request (e.g. Test Setup) or disabled, just proceed
        if (attributes == null || isDisabled(joinPoint)) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String reference = request.getHeader(IDEMPOTENCY_HEADER);

        if (reference == null || reference.isBlank()) {
            throw new BadRequestException(IDEMPOTENCY_HEADER + " is required");
        }

        // RECURSION CHECK: If this thread is already processing this specific key,
        // bypass the cache.get() to avoid the "Recursive Update" IllegalStateException.
        if (PROCESSING_KEYS.get().contains(reference)) {
            return joinPoint.proceed();
        }

        try {
            PROCESSING_KEYS.get().add(reference);

            return idempotencyStore.get(reference, () -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable t) {
                    // Wrap checked exceptions so the Supplier can throw them
                    throw (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t);
                }
            });
        } finally {
            PROCESSING_KEYS.get().remove(reference);
            if (PROCESSING_KEYS.get().isEmpty()) {
                PROCESSING_KEYS.remove();
            }
        }
    }

    private boolean isDisabled(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().isAnnotationPresent(DisableFraudDetection.class);
    }
}