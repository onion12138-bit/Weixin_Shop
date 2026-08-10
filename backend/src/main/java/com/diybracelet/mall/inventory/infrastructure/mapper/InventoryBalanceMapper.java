package com.diybracelet.mall.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.inventory.domain.InventoryBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * SKU 库存余额数据访问入口。
 */
@Mapper
public interface InventoryBalanceMapper
        extends BaseMapper<InventoryBalance> {

    /**
     * 锁定单个 SKU 库存行，保证同一 SKU 的并发下单串行扣减。
     */
    @Select("""
            select *
            from inventory_balance
            where bead_sku_id = #{skuId}
            for update
            """)
    InventoryBalance selectBySkuForUpdate(@Param("skuId") Long skuId);

    /**
     * 管理后台入库时只增加可用库存，预占和已售库存保持不变。
     */
    @Update("""
            update inventory_balance
            set available_qty = available_qty + #{quantity},
                version = version + 1
            where bead_sku_id = #{skuId}
            """)
    int addAvailable(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

    /**
     * 把可用库存转为预占库存。
     */
    @Update("""
            update inventory_balance
            set available_qty = available_qty - #{quantity},
                reserved_qty = reserved_qty + #{quantity},
                version = version + 1
            where bead_sku_id = #{skuId}
              and available_qty >= #{quantity}
            """)
    int reserve(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

    /**
     * 支付成功后，把订单预占量转入已售量。
     */
    @Update("""
            update inventory_balance
            set reserved_qty = reserved_qty - #{quantity},
                sold_qty = sold_qty + #{quantity},
                version = version + 1
            where bead_sku_id = #{skuId}
              and reserved_qty >= #{quantity}
            """)
    int confirmSale(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

    /**
     * 支付前取消订单时，把预占量退回可用量。
     */
    @Update("""
            update inventory_balance
            set available_qty = available_qty + #{quantity},
                reserved_qty = reserved_qty - #{quantity},
                version = version + 1
            where bead_sku_id = #{skuId}
              and reserved_qty >= #{quantity}
            """)
    int release(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );
}
