package com.diybracelet.mall.file.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据的数据访问入口。
 */
@Mapper
public interface AssetFileMapper extends BaseMapper<AssetFile> {
}
