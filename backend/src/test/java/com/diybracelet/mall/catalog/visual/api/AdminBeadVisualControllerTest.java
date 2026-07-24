package com.diybracelet.mall.catalog.visual.api;

import com.diybracelet.mall.catalog.visual.domain.BeadVisualStatus;
import com.diybracelet.mall.catalog.visual.domain.BeadVisualVersion;
import com.diybracelet.mall.catalog.visual.infrastructure.BeadVisualVersionMapper;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.file.domain.FileStorage;
import com.diybracelet.mall.file.domain.StoredFile;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({AdminBeadVisualController.class, AdminBeadVisualControllerTest.StorageConfiguration.class})
class AdminBeadVisualControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BeadVisualVersionMapper visualVersionMapper;

    @Autowired
    private InMemoryFileStorage fileStorage;

    @Test
    void shouldCreateManualVersionAndPublishIt() throws Exception {
        String token = jwtService.issueAccessToken("admin");
        insertActivePng(901L, "tray.png", 160, 160);
        insertActivePng(902L, "canvas.png", 512, 512);

        String response = mockMvc.perform(post("/api/admin/catalog/beads/2001/visuals/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trayAssetId":901,"canvasAssetId":902}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Long versionId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/catalog/beads/2001/visuals/" + versionId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        assertThat(jdbcTemplate.queryForObject(
                "select published_visual_version_id from bead_sku where id = 2001",
                Long.class
        )).isEqualTo(versionId);

        mockMvc.perform(get("/api/public/catalog/beads/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diyVisual.version").value(1))
                .andExpect(jsonPath("$.data.diyVisual.trayUrl")
                        .value("/api/public/files/tray.png"))
                .andExpect(jsonPath("$.data.diyVisual.canvasUrl")
                        .value("/api/public/files/canvas.png"));
    }

    @Test
    void shouldArchivePriorVersionAndRejectVersionForAnotherSku() throws Exception {
        String token = jwtService.issueAccessToken("admin");
        insertActivePng(911L, "tray-one.png", 160, 160);
        insertActivePng(912L, "canvas-one.png", 512, 512);
        insertActivePng(913L, "tray-two.png", 160, 160);
        insertActivePng(914L, "canvas-two.png", 512, 512);

        Long firstId = createManual(token, 911L, 912L);
        publish(token, 2001L, firstId);
        Long secondId = createManual(token, 913L, 914L);
        publish(token, 2001L, secondId);

        BeadVisualVersion first = visualVersionMapper.selectById(firstId);
        assertThat(first.getStatus()).isEqualTo(BeadVisualStatus.ARCHIVED);

        mockMvc.perform(post("/api/admin/catalog/beads/2004/visuals/" + secondId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"));
    }

    private Long createManual(String token, Long trayAssetId, Long canvasAssetId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/catalog/beads/2001/visuals/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trayAssetId\":%d,\"canvasAssetId\":%d}".formatted(trayAssetId, canvasAssetId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("id").asLong();
    }

    private void publish(String token, Long skuId, Long versionId) throws Exception {
        mockMvc.perform(post("/api/admin/catalog/beads/" + skuId + "/visuals/" + versionId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void insertActivePng(Long id, String storageKey, int width, int height) {
        fileStorage.put(storageKey, png(width, height));
        jdbcTemplate.update("""
                insert into asset_file (
                    id, storage_key, original_name, content_type,
                    size_bytes, public_url, status
                ) values (?, ?, ?, 'image/png', ?, ?, 'ACTIVE')
                """, id, storageKey, storageKey, fileStorage.read(storageKey).length,
                "/api/public/files/" + storageKey);
    }

    private byte[] png(int width, int height) {
        ByteBuffer bytes = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN);
        bytes.put(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        bytes.putInt(13);
        bytes.put(new byte[] {0x49, 0x48, 0x44, 0x52});
        bytes.putInt(width);
        bytes.putInt(height);
        bytes.put((byte) 8);
        bytes.put((byte) 6);
        bytes.put((byte) 0);
        bytes.put((byte) 0);
        bytes.put((byte) 0);
        bytes.putInt(0);
        return bytes.array();
    }

    @TestConfiguration
    static class StorageConfiguration {
        @Bean
        @Primary
        InMemoryFileStorage fileStorage() {
            return new InMemoryFileStorage();
        }
    }

    static class InMemoryFileStorage implements FileStorage {
        private final Map<String, byte[]> content = new HashMap<>();

        @Override
        public StoredFile store(String originalName, String contentType, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] read(String storageKey) {
            return content.get(storageKey);
        }

        void put(String storageKey, byte[] bytes) {
            content.put(storageKey, bytes);
        }
    }
}
