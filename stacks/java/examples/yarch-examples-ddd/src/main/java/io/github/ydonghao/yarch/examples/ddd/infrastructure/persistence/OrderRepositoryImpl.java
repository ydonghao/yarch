package io.github.ydonghao.yarch.examples.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.ydonghao.yarch.examples.ddd.domain.model.Order;
import io.github.ydonghao.yarch.examples.ddd.domain.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper mapper;

    @Override
    public Order save(Order order) {
        OrderPO po = OrderPO.fromDomain(order);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        return po.toDomain();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(OrderPO::toDomain);
    }

    @Override
    public List<Order> pageAfter(Long cursor, int pageSize) {
        // keyset 分页（PG 规约三-7）：id > cursor order by id limit n，深分页不走 offset
        return mapper
                .selectList(
                        Wrappers.<OrderPO>query()
                                .gt(cursor != null, "id", cursor)
                                .orderByAsc("id")
                                .last("limit " + pageSize))
                .stream()
                .map(OrderPO::toDomain)
                .toList();
    }
}
