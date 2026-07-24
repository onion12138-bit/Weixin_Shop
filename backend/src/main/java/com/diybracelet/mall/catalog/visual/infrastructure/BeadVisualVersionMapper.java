package com.diybracelet.mall.catalog.visual.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 散珠视觉版本的数据访问入口。 */
@Mapper
public interface BeadVisualVersionMapper extends BaseMapper<BeadVisualVersion> {

    @Select("""
        select * from bead_visual_version
         where id = #{id}
         for update
        """)
    BeadVisualVersion selectByIdForUpdate(@Param("id") Long id);

    @Select("""
        select * from bead_visual_version
         where bead_sku_id = #{beadSkuId}
         order by version_no desc
        """)
    List<BeadVisualVersion> selectByBeadSkuId(@Param("beadSkuId") Long beadSkuId);
}
