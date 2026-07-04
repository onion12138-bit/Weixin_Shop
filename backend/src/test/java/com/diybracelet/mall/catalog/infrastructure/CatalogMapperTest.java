package com.diybracelet.mall.catalog.infrastructure;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 目录持久层集成测试。
 *
 * <p>该测试使用真实 MySQL 8 和 Flyway 演示数据，覆盖公开商品列表最关键的可见性规则：
 * 查询只能返回已上架商品，同时分类与关键词条件必须共同生效。这样可以避免仅在内存数据库
 * 上通过、部署到 MySQL 后因 JSON、枚举或 SQL 方言差异而失败。</p>
 */
class CatalogMapperTest extends MySqlIntegrationTest {

    @Autowired
    private BeadSkuMapper beadSkuMapper;

    /**
     * 验证公开列表按分类和关键词筛选，并且不会泄露草稿或已下架商品。
     */
    @Test
    void shouldFilterVisibleBeadsByCategoryAndKeyword() {
        var result = beadSkuMapper.selectPublicPage(1001L, "水晶", 0, 20);

        assertThat(result)
                .isNotEmpty()
                .allSatisfy(bead -> {
                    assertThat(bead.getCategoryId()).isEqualTo(1001L);
                    assertThat(bead.getStatus()).isEqualTo(CatalogStatus.ON_SALE);
                    assertThat(bead.getName() + bead.getMaterial()).contains("水晶");
                });
    }
}
