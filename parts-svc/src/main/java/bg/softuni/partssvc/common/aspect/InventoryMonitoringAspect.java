package bg.softuni.partssvc.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class InventoryMonitoringAspect {

    private static final long SLOW_CALL_MILLIS = 400;

    @Pointcut("within(@org.springframework.stereotype.Service bg.softuni.partssvc..*)")
    public void serviceLayer() {
    }

    @Around("serviceLayer()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        Object result = joinPoint.proceed();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        if (elapsedMillis >= SLOW_CALL_MILLIS) {
            log.warn("{} took {} ms", signatureOf(joinPoint), elapsedMillis);
        } else if (log.isDebugEnabled()) {
            log.debug("{} took {} ms", signatureOf(joinPoint), elapsedMillis);
        }
        return result;
    }

    @AfterThrowing(pointcut = "serviceLayer()", throwing = "exception")
    public void onFailure(JoinPoint joinPoint, Throwable exception) {
        log.warn("{} rejected the call: {}", signatureOf(joinPoint), exception.getMessage());
    }

    private String signatureOf(JoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }
}
