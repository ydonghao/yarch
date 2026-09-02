package io.github.yuandonghao.yarch.examples.ddd.domain.model;

import java.time.Instant;

/** 领域模型（充血但克制）：业务规则在模型/领域服务，零框架依赖（ArchUnit 守护） */
public record Product(Long id, String name, long priceCents, int stock, Instant createdAt) {

    /** 下单扣减库存：返回扣减后的新值（不修改自身，record 不可变） */
    public Product deduct(int quantity) {
        return new Product(id, name, priceCents, stock - quantity, createdAt);
    }
}
