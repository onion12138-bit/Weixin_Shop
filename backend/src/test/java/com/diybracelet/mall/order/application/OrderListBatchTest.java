package com.diybracelet.mall.order.application;

import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.diybracelet.mall.order.domain.CustomerOrder;
import com.diybracelet.mall.order.domain.OrderItem;
import com.diybracelet.mall.order.domain.FulfillmentTask;
import com.diybracelet.mall.order.infrastructure.mapper.CustomerOrderMapper;
import com.diybracelet.mall.order.infrastructure.mapper.OrderItemMapper;
import com.diybracelet.mall.order.infrastructure.mapper.FulfillmentTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 订单列表批量读取后仍正确关联明细和物流，查询数不随订单数增加。 */
@ExtendWith(MockitoExtension.class)
class OrderListBatchTest {
    @Mock CustomerIdentityResolver identityResolver;
    @Mock CustomerOrderMapper orderMapper;
    @Mock OrderItemMapper itemMapper;
    @Mock FulfillmentTaskMapper fulfillmentMapper;
    @InjectMocks OrderService service;

    @Test
    void associatesEachOrderWithItsOwnItemsAndOptionalShippingInThreeQueries() {
        when(identityResolver.requireCustomerIdentityId()).thenReturn(7L);
        CustomerOrder newest = order(20L);
        CustomerOrder older = order(10L);
        when(orderMapper.selectOwnedOrders(7L)).thenReturn(List.of(newest, older));
        OrderItem first = item(10L, "older-bead");
        OrderItem second = item(20L, "newest-bead");
        when(itemMapper.selectOwnedByCustomer(7L)).thenReturn(List.of(first, second));
        FulfillmentTask delivery = new FulfillmentTask();
        delivery.setOrderId(10L);
        delivery.setTrackingNo("TRACK-10");
        when(fulfillmentMapper.selectOwnedByCustomer(7L)).thenReturn(List.of(delivery));

        var result = service.listOwned();

        assertThat(result).extracting(r -> r.id()).containsExactly(20L, 10L);
        assertThat(result.get(0).items()).extracting(i -> i.name()).containsExactly("newest-bead");
        assertThat(result.get(0).trackingNo()).isNull();
        assertThat(result.get(1).items()).extracting(i -> i.name()).containsExactly("older-bead");
        assertThat(result.get(1).trackingNo()).isEqualTo("TRACK-10");
        verify(orderMapper).selectOwnedOrders(7L);
        verify(itemMapper).selectOwnedByCustomer(7L);
        verify(fulfillmentMapper).selectOwnedByCustomer(7L);
        verifyNoMoreInteractions(orderMapper, itemMapper, fulfillmentMapper);
    }

    @Test
    void emptyOrdersSkipDetailAndShippingQueries() {
        when(identityResolver.requireCustomerIdentityId()).thenReturn(7L);
        when(orderMapper.selectOwnedOrders(7L)).thenReturn(List.of());
        assertThat(service.listOwned()).isEmpty();
        verifyNoInteractions(itemMapper, fulfillmentMapper);
    }

    private CustomerOrder order(long id) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        return order;
    }

    private OrderItem item(long orderId, String name) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setName(name);
        return item;
    }
}
