package com.diybracelet.mall.catalog.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.catalog.domain.ProductPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成品手串套餐的数据访问入口。
 */
@Mapper
public interface ProductPackageMapper extends BaseMapper<ProductPackage> {

    /**
     * 分页查询已上架成品套餐。
     *
     * @param offset 从零开始的偏移量
     * @param limit 最大返回数量
     * @return 已上架套餐
     */
    @Select("""
            select *
            from product_package
            where status = 'ON_SALE'
            order by id desc
            limit #{limit} offset #{offset}
            """)
    List<ProductPackage> selectPublicPage(
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /**
     * 查询后台标记为首页推荐且仍处于上架状态的成品。
     *
     * @param limit 最大返回数量
     * @return 首页推荐成品，最近设置推荐的排在前面
     */
    @Select("""
            select *
            from product_package
            where status = 'ON_SALE'
              and recommended = true
            order by updated_at desc, id desc
            limit #{limit}
            """)
    List<ProductPackage> selectRecommended(@Param("limit") int limit);

    /**
     * @return 已上架成品套餐总数
     */
    @Select("""
            select count(*)
            from product_package
            where status = 'ON_SALE'
            """)
    long countPublic();
}
