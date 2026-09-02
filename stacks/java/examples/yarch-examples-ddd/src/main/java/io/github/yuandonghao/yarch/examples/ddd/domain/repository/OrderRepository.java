package io.github.yuandonghao.yarch.examples.ddd.domain.repository;

import io.github.yuandonghao.yarch.examples.ddd.domain.model.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    /** keyset 通道：id 严格大于 cursor 的下一窗口（游标 null 表示从头） */
    List<Order> pageAfter(Long cursor, int pageSize);
}
