package com.jshz.expert.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * 分页响应结构 {total, items}（接口全集对齐 demo/设计文档）。
 */
@Data
public class PageResult<T> {

    private long total;

    private List<T> items;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setItems(page.getRecords());
        return result;
    }

    public static <T> PageResult<T> of(long total, List<T> items) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setItems(items);
        return result;
    }
}
