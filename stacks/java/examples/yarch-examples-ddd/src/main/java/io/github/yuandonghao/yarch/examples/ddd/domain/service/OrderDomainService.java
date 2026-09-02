package io.github.yuandonghao.yarch.examples.ddd.domain.service;

import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Order;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import io.github.yuandonghao.yarch.examples.ddd.types.errno.ExampleErrorCode;

/** 领域服务：下单规则（存在性 + 库存充足性），纯 Java 可独立单测 */
public class OrderDomainService {

    public Order place(Product product, String buyerEmail, int quantity) {
        if (product.stock() < quantity) {
            throw new BusinessException(
                    ExampleErrorCode.STOCK_INSUFFICIENT,
                    product.id() + " 剩余 " + product.stock() + " 需 " + quantity);
        }
        return Order.place(product.id(), buyerEmail, quantity);
    }
}
