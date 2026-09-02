package io.github.yuandonghao.yarch.web.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等存储 SPI：web starter 定义，yarch-redis starter 提供 Redis 实现（SET NX PX）， 默认降级为进程内实现（单实例语义）。业务工程可自行替换。
 */
public interface IdempotencyStore {

    /** 查询已存储的幂等条目（PENDING 或 DONE 均返回，由调用方判断状态） */
    Optional<StoredResponse> find(String key);

    /** 占位（首次进入）：true = 占位成功即本请求负责执行与存储；false = 已有同键条目 */
    boolean reserve(String key, String requestHash, Duration ttl);

    /** 存储执行结果（覆盖 PENDING 占位） */
    void save(
            String key,
            String requestHash,
            int status,
            String contentType,
            String body,
            Duration ttl);

    /** 丢弃占位（执行失败时释放，允许重试） */
    void discard(String key);

    /**
     * @param requestHash 请求摘要（method+uri+query+body 的 SHA-256）；同键异参即 1007 的判据
     * @param status null = PENDING（并发竞争等待中）；非 null = 已完成可回放
     */
    record StoredResponse(String requestHash, Integer status, String contentType, String body) {

        public boolean pending() {
            return status == null;
        }
    }
}
