package com.diybracelet.mall.catalog.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.catalog.domain.BeadSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 散珠 SKU 数据访问入口。
 */
@Mapper
public interface BeadSkuMapper extends BaseMapper<BeadSku> {

    /**
     * 查询小程序公开可见的散珠。
     *
     * <p>上架条件在 SQL 层强制执行，避免调用方遗漏状态过滤而泄露草稿。
     * 分类和关键词均为可选条件，分页参数由应用服务完成边界校验后传入。</p>
     *
     * @param categoryId 分类主键；为 {@code null} 时查询全部分类
     * @param keyword    名称或材质关键词；为 {@code null} 或空串时不筛选
     * @param offset     从零开始的结果偏移量
     * @param limit      本次最多返回的记录数
     * @return 按主键倒序排列的已上架散珠
     */
    @Select("""
            <script>
            select bead.*
            from bead_sku bead
            inner join category category
              on category.id = bead.category_id
             and category.status = 'ON_SALE'
            where bead.status = 'ON_SALE'
            <if test="categoryId != null">
              and bead.category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
              and (
                bead.name like concat('%', #{keyword}, '%')
                or bead.material like concat('%', #{keyword}, '%')
              )
            </if>
            order by bead.id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    List<BeadSku> selectPublicPage(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /**
     * 统计与公开列表完全相同条件下的散珠数量。
     *
     * @param categoryId 可选分类
     * @param keyword 可选名称或材质关键词
     * @return 匹配记录数
     */
    @Select("""
            <script>
            select count(*)
            from bead_sku bead
            inner join category category
              on category.id = bead.category_id
             and category.status = 'ON_SALE'
            where bead.status = 'ON_SALE'
            <if test="categoryId != null">
              and bead.category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
              and (
                bead.name like concat('%', #{keyword}, '%')
                or bead.material like concat('%', #{keyword}, '%')
              )
            </if>
            </script>
            """)
    long countPublic(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );
}
