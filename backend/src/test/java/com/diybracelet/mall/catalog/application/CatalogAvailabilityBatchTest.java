package com.diybracelet.mall.catalog.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.catalog.visual.query.PublishedBeadVisualQuery;
import com.diybracelet.mall.inventory.domain.InventoryBalance;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A catalog page must expose availability with one bounded, read-only inventory query. */
@ExtendWith(MockitoExtension.class)
class CatalogAvailabilityBatchTest {
    @Mock BeadSkuMapper beads;
    @Mock InventoryBalanceMapper inventory;
    @Mock PublishedBeadVisualQuery visuals;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldResolveAllPageInventoryInOneQueryWithoutPrivateInventoryFields() {
        List<BeadSku> page = List.of(bead(2001L), bead(2002L), bead(2003L));
        when(beads.countPublic(null, null)).thenReturn(3L);
        when(beads.selectPublicPage(null, null, 0L, 20L)).thenReturn(page);
        when(visuals.resolve(page)).thenReturn(Map.of());
        when(inventory.selectList(any())).thenReturn(List.of(balance(2002L, 9), balance(2001L, 4)));

        var result = service().listBeads(null, null, 1, 20);

        assertThat(result.items()).extracting(item -> item.availableQty()).containsExactly(4, 9, 0);
        ArgumentCaptor<QueryWrapper> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(inventory).selectList(query.capture());
        assertThat(query.getValue().getSqlSelect()).isEqualTo("bead_sku_id,available_qty");
        verifyNoMoreInteractions(inventory);
    }

    @Test
    void shouldSkipInventoryReadForAnEmptyPage() {
        when(beads.selectPublicPage(null, null, 0L, 20L)).thenReturn(List.of());
        when(visuals.resolve(List.of())).thenReturn(Map.of());
        assertThat(service().listBeads(null, null, 1, 20).items()).isEmpty();
        verifyNoInteractions(inventory);
    }

    private CatalogQueryService service() {
        return new CatalogQueryService(null, beads, null, null, null, visuals, new ObjectMapper(), inventory);
    }

    private BeadSku bead(Long id) {
        BeadSku bead = new BeadSku();
        bead.setId(id);
        bead.setTagsJson("[]");
        return bead;
    }

    private InventoryBalance balance(Long id, int available) {
        InventoryBalance balance = new InventoryBalance();
        balance.setBeadSkuId(id);
        balance.setAvailableQty(available);
        return balance;
    }
}
