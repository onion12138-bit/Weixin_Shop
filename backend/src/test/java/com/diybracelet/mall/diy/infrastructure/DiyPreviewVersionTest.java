package com.diybracelet.mall.diy.infrastructure;

import com.diybracelet.mall.diy.infrastructure.mapper.DiySchemeMapper;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DiyPreviewVersionTest extends MySqlIntegrationTest {
    @Autowired private DiySchemeMapper schemes;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.update("""
                insert into customer_identity(id, source, external_key_hash, status)
                values (99001, 'GUEST', ?, 'ACTIVE')
                """, "preview-test".repeat(5));
        for (long id : new long[]{99001, 99002}) {
            jdbc.update("""
                    insert into asset_file(id, storage_key, original_name, content_type, size_bytes, public_url)
                    values (?, ?, 'preview.png', 'image/png', 10, '/preview.png')
                    """, id, "test-preview-" + id);
        }
        jdbc.update("""
                insert into diy_scheme(id, customer_identity_id, name, wrist_circumference_mm,
                  fit_type, cord_option_id, sequence_json, bead_count, catalog_snapshot_json,
                  quote_status, status, version)
                values (99001, 99001, '预览测试', 160, 'STANDARD', 6001, '[2001]', 1, '[]',
                  'NOT_QUOTED', 'ACTIVE', 0)
                """);
    }

    @Test
    void previewDoesNotConflictWithEditingAndLateOldPreviewCannotOverwriteNewDesign() {
        assertThat(schemes.bindPreviewForVersion(99001L, 99001L, 99001L, 0L)).isEqualTo(1);
        var saved = schemes.selectOwnedById(99001L, 99001L);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getPreviewAssetId()).isEqualTo(99001L);
        saved.setSequenceJson("[2001,2001]");
        saved.setBeadCount(2);
        assertThat(schemes.updateOwnedWithVersion(saved, 99001L, 0L)).isEqualTo(1);
        assertThat(schemes.selectOwnedById(99001L, 99001L).getPreviewAssetId()).isNull();
        assertThat(schemes.bindPreviewForVersion(99001L, 99001L, 99001L, 0L)).isZero();
        assertThat(schemes.bindPreviewForVersion(99001L, 99001L, 99002L, 1L)).isEqualTo(1);
        var latest = schemes.selectOwnedById(99001L, 99001L);
        assertThat(latest.getVersion()).isEqualTo(1L);
        assertThat(latest.getPreviewAssetId()).isEqualTo(99002L);
    }

    @Test
    void cannotAttachToAnotherCustomerOrToADeletedOrOrderedScheme() {
        assertThat(schemes.bindPreviewForVersion(99001L, 123L, 99001L, 0L)).isZero();
        assertThat(schemes.softDeleteOwned(99001L, 99001L)).isEqualTo(1);
        assertThat(schemes.bindPreviewForVersion(99001L, 99001L, 99001L, 1L)).isZero();
        assertThat(schemes.selectById(99001L).getPreviewAssetId()).isNull();
    }

    @Test
    void legacyUploadKeepsItsVersionContractAndInvalidatesOlderBackgroundJobs() {
        assertThat(schemes.bindPreviewOwned(99001L, 99001L, 99001L)).isEqualTo(1);
        assertThat(schemes.selectOwnedById(99001L, 99001L).getVersion()).isEqualTo(1L);
        assertThat(schemes.bindPreviewForVersion(99001L, 99001L, 99002L, 0L)).isZero();
    }
}
