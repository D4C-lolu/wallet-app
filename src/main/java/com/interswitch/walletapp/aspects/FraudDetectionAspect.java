package com.interswitch.walletapp.aspects;

import com.interswitch.walletapp.annotation.DisableFraudDetection;
import com.interswitch.walletapp.context.IpContextHolder;
import com.interswitch.walletapp.exceptions.FraudDetectedException;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.services.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FraudDetectionAspect {

    private final FraudDetectionService fraudDetectionService;

    @Around("@annotation(com.interswitch.walletapp.annotation.FraudChecked)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isDisabled(joinPoint)) {
            return joinPoint.proceed();
        }
        TransferRequest request = extractTransferRequest(joinPoint);

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                UUID.randomUUID().toString(),
                request.fromAccountNumber(),
                request.amount(),
                request.currency(),
                request.cardNumber(),
                IpContextHolder.get(),
                OffsetDateTime.now()
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        if (status == FraudStatus.BLOCKED) {
            throw new FraudDetectedException("Transaction blocked by fraud detection");
        }

        return joinPoint.proceed();
    }

    private boolean isDisabled(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().isAnnotationPresent(DisableFraudDetection.class);
    }

    private TransferRequest extractTransferRequest(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
                .filter(arg -> arg instanceof TransferRequest)
                .map(arg -> (TransferRequest) arg)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No TransferRequest found in method args"));
    }
}