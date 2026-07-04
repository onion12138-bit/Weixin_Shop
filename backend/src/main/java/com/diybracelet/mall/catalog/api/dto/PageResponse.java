package com.diybracelet.mall.catalog.api.dto;

import java.util.List;

/**
 * 三端共用的稳定分页响应。
 *
 * @param items 当前页数据
 * @param total 满足筛选条件的总记录数
 * @param page 从 1 开始的页码
 * @param size 每页数量
 * @param <T> 列表项类型
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
}
