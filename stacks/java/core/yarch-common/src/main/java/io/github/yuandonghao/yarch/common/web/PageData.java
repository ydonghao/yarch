package io.github.yuandonghao.yarch.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页负载形状（契约：rest-response.md「分页负载形状」+ rest-conventions.md「查询约定」）。 list 可为空数组不得为 null；nextCursor
 * 缺失或空串表示没有下一页（游标通道，页码通道恒省略）。
 */
public class PageData<T> {

    private List<T> list;
    private long total;
    private int page;
    private int pageSize;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nextCursor;

    public static <T> PageData<T> of(List<T> list, long total, int page, int pageSize) {
        PageData<T> p = new PageData<>();
        p.list = list == null ? new ArrayList<>() : list;
        p.total = total;
        p.page = page;
        p.pageSize = pageSize;
        return p;
    }

    /** 游标通道：nextCursor 仅在可能还有下一页时给出（rows 满页） */
    public static <T> PageData<T> ofKeyset(List<T> rows, int pageSize, String nextCursor) {
        PageData<T> p = new PageData<>();
        p.list = rows == null ? new ArrayList<>() : rows;
        p.total = -1L; // keyset 通道不承诺精确 total
        p.page = 1;
        p.pageSize = pageSize;
        p.nextCursor = (nextCursor == null || nextCursor.isEmpty()) ? null : nextCursor;
        return p;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
