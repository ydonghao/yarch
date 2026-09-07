package io.github.ydonghao.yarch.examples.ddd.domain.model;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.examples.ddd.types.errno.ExampleErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 领域状态机（E5 教学中间档：状态字段 → 状态机 → 流程引擎三档中的第二档）。 转移规则表内聚在领域模型（纯 Java 可单测）；非法迁移抛业务码 3004。
 * 复杂流程（会签/委派/回退）不在此档硬扛——升级路径见 docs 对标 E5。
 */
public record Order(
        Long id,
        Long productId,
        String buyerEmail,
        int quantity,
        String status,
        Instant createdAt) {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_CANCELLED = "cancelled";

    /** 允许转移表：from → {to...} */
    private static final Map<String, Set<String>> TRANSITIONS =
            Map.of(
                    STATUS_PENDING, Set.of(STATUS_PAID, STATUS_CANCELLED),
                    STATUS_PAID, Set.of(),
                    STATUS_CANCELLED, Set.of());

    public static Order place(Long productId, String buyerEmail, int quantity) {
        return new Order(null, productId, buyerEmail, quantity, STATUS_PENDING, null);
    }

    public Order pay() {
        return transitionTo(STATUS_PAID);
    }

    public Order cancel() {
        return transitionTo(STATUS_CANCELLED);
    }

    private Order transitionTo(String target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new BusinessException(
                    ExampleErrorCode.INVALID_STATE,
                    status + " -> " + target + " (order " + id + ")");
        }
        return new Order(id, productId, buyerEmail, quantity, target, createdAt);
    }
}
