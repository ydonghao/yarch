package io.github.ydonghao.yarch.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询基类（契约 rest-conventions.md「查询约定」）：页码通道 1-based； pageSize 默认 20、上限 100；参数非法（非正整数 /
 * 超上限）由全局异常处理收敛为 1001/400—— 注意与 D6 的边界：page 超出总页数是「合法参数 + 边界事实」，返回 200 + 空 list + 真实 total，不报错。
 *
 * <p>用法：{@code public RestResponse<PageData<Foo>> list(@Valid PageQuery query)}
 */
public class PageQuery {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    @Min(value = 1, message = "page 必须为正整数")
    private int page = 1;

    @Min(value = 1, message = "pageSize 必须为正整数")
    @Max(value = MAX_PAGE_SIZE, message = "pageSize 必须 ≤ 100")
    private int pageSize = DEFAULT_PAGE_SIZE;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /** 计算页码通道 offset（分页必须下推数据库，禁内存分页——postgresql.md 五-5） */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
