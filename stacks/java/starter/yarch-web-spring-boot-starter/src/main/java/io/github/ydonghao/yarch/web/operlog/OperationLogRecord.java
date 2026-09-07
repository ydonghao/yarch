package io.github.ydonghao.yarch.web.operlog;

/** 操作日志记录（存储 SPI 的载荷；traceId 由记录时的 MDC 带出） */
public record OperationLogRecord(
        String action, String method, long costMs, boolean success, String error, String traceId) {}
