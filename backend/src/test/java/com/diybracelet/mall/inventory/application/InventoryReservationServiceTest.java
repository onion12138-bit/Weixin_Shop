package com.diybracelet.mall.inventory.application;

import com.diybracelet.mall.inventory.domain.InventoryBalance;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryLedgerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 验证多 SKU 库存操作始终使用稳定锁顺序，降低并发订单死锁概率。
 */
@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

    @Mock
    private InventoryBalanceMapper balanceMapper;

    @Mock
    private InventoryLedgerMapper ledgerMapper;

    @InjectMocks
    private InventoryReservationService service;

    @Test
    void shouldLockInventoryRowsInAscendingSkuOrder() {
        Map<Long, Integer> reverseInput = new LinkedHashMap<>();
        reverseInput.put(2002L, 9);
        reverseInput.put(2001L, 12);
        when(balanceMapper.selectBySkuForUpdate(2001L))
                .thenReturn(balance(2001L));
        when(balanceMapper.selectBySkuForUpdate(2002L))
                .thenReturn(balance(2002L));
        when(balanceMapper.reserve(2001L, 12)).thenReturn(1);
        when(balanceMapper.reserve(2002L, 9)).thenReturn(1);

        service.reserve(99L, reverseInput);

        InOrder locks = inOrder(balanceMapper);
        locks.verify(balanceMapper).selectBySkuForUpdate(2001L);
        locks.verify(balanceMapper).reserve(2001L, 12);
        locks.verify(balanceMapper).selectBySkuForUpdate(2002L);
        locks.verify(balanceMapper).reserve(2002L, 9);
    }

    private InventoryBalance balance(Long skuId) {
        InventoryBalance balance = new InventoryBalance();
        balance.setBeadSkuId(skuId);
        balance.setAvailableQty(200);
        balance.setReservedQty(0);
        balance.setSoldQty(0);
        balance.setVersion(0L);
        return balance;
    }
}
