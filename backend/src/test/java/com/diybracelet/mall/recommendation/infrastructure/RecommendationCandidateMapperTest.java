package com.diybracelet.mall.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diybracelet.mall.recommendation.domain.RecommendationCandidate;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

/** Real MySQL proof that both candidate queries require a usable public cover. */
@Transactional
@Sql(config = @SqlConfig(separator = "^^^"), statements = {
    """
    insert into category (
        id, name, slug, product_type, sort_order, status
    ) values (29100, '测试饰品', 'test-accessory', 'ACCESSORY', 999, 'ON_SALE')
    ^^^
    """,
    """
    insert into asset_file (
        id, storage_key, original_name, content_type, size_bytes, public_url, status
    ) values
        (29101, 'recommend/active.png', 'active.png', 'image/png', 10, '/api/public/files/active.png', 'ACTIVE'),
        (29102, 'recommend/inactive.png', 'inactive.png', 'image/png', 10, '/api/public/files/inactive.png', 'DISABLED'),
        (29103, 'recommend/empty.png', 'empty.png', 'image/png', 10, '', 'ACTIVE'),
        (29104, 'recommend/blank.png', 'blank.png', 'image/png', 10, '   ', 'ACTIVE')
    ^^^
    """,
    """
    insert into bead_sku (
        id, category_id, sku_code, name, material, color, diameter_mm,
        sale_price, cost_price, stock_alert_threshold, cover_asset_id,
        tags_json, status
    ) values
        (29201, 1001, 'RECOMMEND-NO-COVER', '无封面', '天然水晶', '透明', 8, 10, 1, 1, null, json_array('测试'), 'ON_SALE'),
        (29202, 1001, 'RECOMMEND-INACTIVE-COVER', '停用封面', '天然水晶', '透明', 8, 10, 1, 1, 29102, json_array('测试'), 'ON_SALE'),
        (29203, 1001, 'RECOMMEND-EMPTY-COVER', '空地址封面', '天然水晶', '透明', 8, 10, 1, 1, 29103, json_array('测试'), 'ON_SALE'),
        (29204, 1001, 'RECOMMEND-BLANK-COVER', '空白地址封面', '天然水晶', '透明', 8, 10, 1, 1, 29104, json_array('测试'), 'ON_SALE'),
        (29205, 1001, 'RECOMMEND-ACTIVE-COVER', '有效封面', '天然水晶', '透明', 8, 10, 1, 1, 29101, json_array('测试'), 'ON_SALE'),
        (29206, 29100, 'RECOMMEND-ACCESSORY', '测试配件', '合金', '金色', null, 10, 1, 1, 29101, json_array('测试'), 'ON_SALE')
    ^^^
    """,
    """
    insert into inventory_balance (
        id, bead_sku_id, available_qty, reserved_qty, sold_qty, version
    ) values
        (29301, 29201, 5, 0, 0, 0),
        (29302, 29202, 5, 0, 0, 0),
        (29303, 29203, 5, 0, 0, 0),
        (29304, 29204, 5, 0, 0, 0),
        (29305, 29205, 5, 0, 0, 0),
        (29306, 29206, 5, 0, 0, 0)
    ^^^
    """
})
class RecommendationCandidateMapperTest extends MySqlIntegrationTest {

    private static final List<Long> TEST_IDS = List.of(
        29201L, 29202L, 29203L, 29204L, 29205L, 29206L
    );

    @Autowired
    private RecommendationCandidateMapper mapper;

    @Test
    void shouldRequireAnActiveNonBlankPublicCoverInInitialAndCurrentQueries() {
        assertThat(onlyTestRows(mapper.selectSellableCandidates()))
            .extracting(RecommendationCandidate::skuId)
            .containsExactly(29205L);

        assertThat(mapper.selectCurrentSellableByIds(TEST_IDS))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.skuId()).isEqualTo(29205L);
                assertThat(candidate.imageUrl())
                    .isEqualTo("/api/public/files/active.png");
            });
    }

    private List<RecommendationCandidate> onlyTestRows(
            List<RecommendationCandidate> candidates
    ) {
        return candidates.stream()
            .filter(candidate -> TEST_IDS.contains(candidate.skuId()))
            .toList();
    }
}
