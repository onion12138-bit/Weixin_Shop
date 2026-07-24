package com.diybracelet.mall.diy.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.diy.domain.DiyVisualConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** DIY 固定视图渲染配置的数据访问入口。 */
@Mapper
public interface DiyVisualConfigMapper extends BaseMapper<DiyVisualConfig> {

    /** 锁定并读取全局视觉配置，串行化完整替换和版本递增。 */
    @Select("""
            select * from diy_visual_config
            where id = #{id}
            for update
            """)
    DiyVisualConfig selectByIdForUpdate(@Param("id") Long id);
}
