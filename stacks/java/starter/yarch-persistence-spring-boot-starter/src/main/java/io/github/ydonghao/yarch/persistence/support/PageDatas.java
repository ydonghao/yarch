package io.github.ydonghao.yarch.persistence.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ydonghao.yarch.common.web.PageData;
import java.util.List;
import java.util.function.Function;

/**
 * 分页负载装配（契约 PageData 形状）： 页码通道 = MP IPage（count 先行、limit/offset 下推）； keyset 通道 = 游标查询结果 + 满页给
 * nextCursor（PG 规约三-7：深分页优先 keyset）。
 */
public final class PageDatas {

    private PageDatas() {}

    /** 页码通道：IPage → PageData（list 非 null；越界页天然空 list + 真实 total，D6 口径） */
    public static <T> PageData<T> of(IPage<T> page) {
        return PageData.of(
                page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 页码通道 + 元素映射（PO → 领域模型/DTO） */
    public static <P, T> PageData<T> of(IPage<P> page, Function<P, T> mapper) {
        return PageData.of(
                page.getRecords().stream().map(mapper).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    /** 构造 MP 分页参数（1-based；pageSize 上限由 web 层 PageQuery 校验兜底） */
    public static <T> Page<T> mpPage(int page, int pageSize) {
        return new Page<>(page, pageSize);
    }

    /**
     * keyset 通道：rows 为本页结果，满页时以 {@code cursorOf} 产出下一页游标。 用法：{@code where id > lastId order by id
     * limit pageSize} 的结果直接传入。
     */
    public static <T> PageData<T> ofKeyset(
            List<T> rows, int pageSize, Function<T, String> cursorOf) {
        String nextCursor =
                rows.size() >= pageSize && !rows.isEmpty()
                        ? cursorOf.apply(rows.get(rows.size() - 1))
                        : null;
        return PageData.ofKeyset(rows, pageSize, nextCursor);
    }
}
