package com.diybracelet.mall.catalog.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.catalog.domain.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品分类的数据访问入口。
 *
 * <p>继承 MyBatis-Plus 基础 Mapper，提供按主键查询以及后台维护所需的标准增删改能力。</p>
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 返回小程序可见分类，先按后台排序值、再按主键稳定排序。
     *
     * @return 已上架分类
     */
    @Select("""
            select *
            from category
            where status = 'ON_SALE'
              and product_type = 'BEAD'
            order by sort_order asc, id asc
            """)
    List<Category> selectPublicList();
}
