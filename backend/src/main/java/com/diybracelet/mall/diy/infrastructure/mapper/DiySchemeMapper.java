package com.diybracelet.mall.diy.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.diy.domain.DiyScheme;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * DIY 方案数据访问入口。
 *
 * <p>所有面向小程序的自定义 SQL 都同时带上 {@code customer_identity_id}
 * 和 {@code status='ACTIVE'}，避免越权读取、修改或删除其他顾客方案。</p>
 */
@Mapper
public interface DiySchemeMapper extends BaseMapper<DiyScheme> {

    /** 串行化同一顾客的新增/复制，避免并发请求一起通过数量检查。 */
    @Select("select id from customer_identity where id = #{customerId} for update")
    Long lockCustomerForSchemeChanges(@Param("customerId") Long customerId);

    /**
     * 查询当前顾客拥有的单个有效方案。
     */
    @Select("""
            select *
            from diy_scheme
            where id = #{id}
              and customer_identity_id = #{customerId}
              and status = 'ACTIVE'
            limit 1
            """)
    DiyScheme selectOwnedById(
            @Param("id") Long id,
            @Param("customerId") Long customerId
    );

    /**
     * 查询当前顾客的有效方案列表，最近更新的方案排在前面。
     */
    @Select("""
            select *
            from diy_scheme
            where customer_identity_id = #{customerId}
              and status = 'ACTIVE'
            order by updated_at desc, id desc
            """)
    List<DiyScheme> selectOwnedList(@Param("customerId") Long customerId);

    /**
     * 统计当前顾客尚未删除的方案数，用于限制可保存的方案数量。
     */
    @Select("""
            select count(*)
            from diy_scheme
            where customer_identity_id = #{customerId}
              and status = 'ACTIVE'
            """)
    long countOwnedActive(@Param("customerId") Long customerId);

    /**
     * 使用乐观锁更新当前顾客拥有的方案。
     *
     * <p>只有请求版本与数据库版本一致时才更新；否则返回 0，由服务层转为 409。</p>
     */
    @Update("""
            update diy_scheme
            set name = #{scheme.name},
                wrist_circumference_mm = #{scheme.wristCircumferenceMm},
                fit_type = #{scheme.fitType},
                cord_option_id = #{scheme.cordOptionId},
                sequence_json = #{scheme.sequenceJson},
                bead_count = #{scheme.beadCount},
                catalog_snapshot_json = #{scheme.catalogSnapshotJson},
                bead_amount = #{scheme.beadAmount},
                craft_fee = #{scheme.craftFee},
                cord_fee = #{scheme.cordFee},
                total_amount = #{scheme.totalAmount},
                quote_fingerprint = #{scheme.quoteFingerprint},
                quote_status = #{scheme.quoteStatus},
                preview_asset_id = null,
                version = version + 1
            where id = #{scheme.id}
              and customer_identity_id = #{customerId}
              and status = 'ACTIVE'
              and version = #{expectedVersion}
            """)
    int updateOwnedWithVersion(
            @Param("scheme") DiyScheme scheme,
            @Param("customerId") Long customerId,
            @Param("expectedVersion") Long expectedVersion
    );

    /**
     * 软删除当前顾客拥有的方案。
     */
    @Update("""
            update diy_scheme
            set status = 'DELETED',
                version = version + 1
            where id = #{id}
              and customer_identity_id = #{customerId}
              and status = 'ACTIVE'
            """)
    int softDeleteOwned(
            @Param("id") Long id,
            @Param("customerId") Long customerId
    );

    /**
     * 为当前顾客拥有的方案绑定预览素材。
     */
    @Update("""
            update diy_scheme
            set preview_asset_id = #{previewAssetId},
                version = version + 1
            where id = #{id}
              and customer_identity_id = #{customerId}
              and status = 'ACTIVE'
            """)
    int bindPreviewOwned(
            @Param("id") Long id,
            @Param("customerId") Long customerId,
            @Param("previewAssetId") Long previewAssetId
    );

    /** 预览是指定设计版本的派生数据，异步补图不推进设计版本。 */
    @Update("""
            update diy_scheme
            set preview_asset_id = #{previewAssetId}
            where id = #{id}
              and customer_identity_id = #{customerId}
              and status = 'ACTIVE'
              and version = #{expectedVersion}
            """)
    int bindPreviewForVersion(
            @Param("id") Long id,
            @Param("customerId") Long customerId,
            @Param("previewAssetId") Long previewAssetId,
            @Param("expectedVersion") Long expectedVersion
    );
}
