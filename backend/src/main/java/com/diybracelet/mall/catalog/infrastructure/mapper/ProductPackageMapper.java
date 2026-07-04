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
     * @return 已上架成品套餐总数
     */
    @Select("""
            select count(*)
            from product_package
            where status = 'ON_SALE'
            """)
    long countPublic();
}
