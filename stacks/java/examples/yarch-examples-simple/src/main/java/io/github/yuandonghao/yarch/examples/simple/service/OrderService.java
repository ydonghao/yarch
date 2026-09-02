package io.github.yuandonghao.yarch.examples.simple.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.examples.simple.dao.OrderMapper;
import io.github.yuandonghao.yarch.examples.simple.manager.StockManager;
import io.github.yuandonghao.yarch.examples.simple.model.OrderDO;
import io.github.yuandonghao.yarch.examples.simple.model.dto.Dtos.CreateOrderRequest;
import io.github.yuandonghao.yarch.examples.simple.model.dto.Dtos.OrderDTO;
import io.github.yuandonghao.yarch.examples.simple.types.errno.ExampleErrorCode;
import io.github.yuandonghao.yarch.persistence.support.PageDatas;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service 层：订单业务——编排 StockManager（通用能力经 manager 下沉，阿里分层纪律） */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final StockManager stockManager;
    private final ProductService productService;

    @Transactional
    public OrderDTO place(CreateOrderRequest request) {
        stockManager.deduct(request.productId(), request.quantity()); // 锁 + 原子扣减
        productService.evict(request.productId()); // 先更新 DB 再删缓存

        OrderDO order = new OrderDO();
        order.setProductId(request.productId());
        order.setBuyerEmail(request.buyerEmail());
        order.setQuantity(request.quantity());
        order.setStatus("pending");
        orderMapper.insert(order);
        return OrderDTO.from(order);
    }

    public OrderDTO requireById(Long id) {
        return OrderDTO.from(requireOrder(orderMapper.selectById(id)));
    }

    /** keyset 通道（深翻页/增量拉取） */
    public PageData<OrderDTO> pageAfter(String cursor, int pageSize) {
        Long lastId = cursor == null || cursor.isBlank() ? null : Long.valueOf(cursor);
        List<OrderDO> rows =
                orderMapper.selectList(
                        Wrappers.<OrderDO>query()
                                .gt(lastId != null, "id", lastId)
                                .orderByAsc("id")
                                .last("limit " + pageSize));
        return PageDatas.ofKeyset(
                rows.stream().map(OrderDTO::from).toList(), pageSize, r -> String.valueOf(r.id()));
    }

    private OrderDO requireOrder(OrderDO order) {
        if (order == null) {
            throw new BusinessException(ExampleErrorCode.ORDER_NOT_FOUND, "not found");
        }
        return order;
    }
}
