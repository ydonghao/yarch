package io.github.yuandonghao.yarch.examples.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import io.github.yuandonghao.yarch.persistence.support.PageDatas;
import io.github.yuandonghao.yarch.examples.ddd.domain.repository.ProductRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper mapper;

    @Override
    public Product save(Product product) {
        ProductPO po = ProductPO.fromDomain(product);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            // G8 变更集更新：只写库存列
            ProductPO patch = new ProductPO();
            patch.setId(po.getId());
            patch.setStock(po.getStock());
            mapper.updateById(patch);
        }
        return po.toDomain();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(ProductPO::toDomain);
    }

    /** 原子扣减兜底（存储层唯一约束/条件更新是幂等最终防线——幂等总则-3） */
    @Override
    public PageData<Product> page(int page, int pageSize) {
        return PageDatas.of(
                mapper.selectPage(PageDatas.mpPage(page, pageSize),
                        Wrappers.<ProductPO>query().orderByAsc("id")),
                ProductPO::toDomain);
    }

    public boolean deductStock(Long productId, int quantity) {
        return mapper.update(
                        null,
                        Wrappers.<ProductPO>update()
                                .setSql("stock = stock - " + quantity)
                                .eq("id", productId)
                                .ge("stock", quantity))
                == 1;
    }
}
