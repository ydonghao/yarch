package io.github.ydonghao.yarch.web.operlog;

import io.github.ydonghao.yarch.common.trace.TraceIds;
import net.logstash.logback.argument.StructuredArguments;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 操作日志切面：ndjson（msg=operation + action/method/costMs/success/error）+ 可选存储 */
@Aspect
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogStore store;

    public OperationLogAspect(OperationLogStore store) {
        this.store = store;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog)
            throws Throwable {
        long start = System.nanoTime();
        String method = joinPoint.getSignature().toShortString();
        try {
            Object result = joinPoint.proceed();
            record(operationLog.action(), method, start, true, null);
            return result;
        } catch (Throwable e) {
            record(
                    operationLog.action(),
                    method,
                    start,
                    false,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private void record(String action, String method, long start, boolean success, String error) {
        long costMs = (System.nanoTime() - start) / 1_000_000;
        String traceId = TraceIds.currentOrEmpty();
        log.info(
                "operation",
                StructuredArguments.kv("action", action),
                StructuredArguments.kv("method", method),
                StructuredArguments.kv("costMs", costMs),
                StructuredArguments.kv("success", success),
                error == null
                        ? StructuredArguments.kv("result", "ok")
                        : StructuredArguments.kv("error", error));
        if (store != null) {
            store.save(new OperationLogRecord(action, method, costMs, success, error, traceId));
        }
    }
}
