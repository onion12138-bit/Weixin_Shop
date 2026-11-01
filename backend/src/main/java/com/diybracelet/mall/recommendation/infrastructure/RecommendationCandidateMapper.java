package com.diybracelet.mall.recommendation.infrastructure;

import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


/** 只读取在售且有库存的候选珠子，供模型选择和结果复核。 */
@Mapper
public interface RecommendationCandidateMapper {

    @Select("""
        select bead.id as skuId,
               bead.name,
               bead.material,
               bead.color,
               bead.diameter_mm as diameterMm,
               bead.sale_price as salePrice,
               bead.tags_json as tagsJson,
               inventory.available_qty as availableQty,
               asset.public_url as imageUrl
        from bead_sku bead
        join category category
          on category.id = bead.category_id
         and category.status = 'ON_SALE'
         and category.product_type = 'BEAD'
        join inventory_balance inventory
          on inventory.bead_sku_id = bead.id
         and inventory.available_qty > 0
        join asset_file asset
          on asset.id = bead.cover_asset_id
         and asset.status = 'ACTIVE'
         and asset.public_url is not null
         and length(trim(asset.public_url)) > 0
        where bead.status = 'ON_SALE'
        order by bead.id asc
        """)
    List<RecommendationCandidate> selectSellableCandidates();

    @Select("""
        <script>
        select bead.id as skuId,
               bead.name,
               bead.material,
               bead.color,
               bead.diameter_mm as diameterMm,
               bead.sale_price as salePrice,
               bead.tags_json as tagsJson,
               inventory.available_qty as availableQty,
               asset.public_url as imageUrl
        from bead_sku bead
        join category category
          on category.id = bead.category_id
         and category.status = 'ON_SALE'
         and category.product_type = 'BEAD'
        join inventory_balance inventory
          on inventory.bead_sku_id = bead.id
         and inventory.available_qty > 0
        join asset_file asset
          on asset.id = bead.cover_asset_id
         and asset.status = 'ACTIVE'
         and asset.public_url is not null
         and length(trim(asset.public_url)) > 0
        where bead.status = 'ON_SALE'
          and bead.id in
          <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
          </foreach>
        order by bead.id asc
        </script>
        """)
    List<RecommendationCandidate> selectCurrentSellableByIds(
        @Param("ids") List<Long> ids
    );
}
