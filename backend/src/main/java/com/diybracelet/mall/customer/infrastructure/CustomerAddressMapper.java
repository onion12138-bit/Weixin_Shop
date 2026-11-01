package com.diybracelet.mall.customer.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.customer.domain.CustomerAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


/** 顾客收货地址数据访问入口。 */
@Mapper
public interface CustomerAddressMapper extends BaseMapper<CustomerAddress> {
    @Select("""
            <script>
            select ca.customer_identity_id
            from customer_address ca
            inner join customer_identity ci
                on ci.id = ca.customer_identity_id
            where ca.status = 'ACTIVE'
              and ci.status = 'ACTIVE'
            <if test="phoneKeyword != null and phoneKeyword != ''">
              and ca.phone like concat('%', #{phoneKeyword}, '%')
            </if>
            group by ca.customer_identity_id
            order by max(ca.updated_at) desc
            limit #{limit}
            </script>
            """)
    List<Long> searchCustomerIdentityIdsByPhone(
            @Param("phoneKeyword") String phoneKeyword,
            @Param("limit") int limit
    );
}
