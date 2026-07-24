package com.diybracelet.mall.catalog.application;

import com.diybracelet.mall.catalog.api.dto.CatalogImportResult;
import com.diybracelet.mall.catalog.api.dto.CreateBeadSkuRequest;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.Category;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogExcelImportServiceTest {

    @Mock
    private AdminCatalogService adminCatalogService;

    @Mock
    private Validator validator;

    private CatalogExcelImportService service;

    @BeforeEach
    void setUp() {
        service = new CatalogExcelImportService(adminCatalogService, validator);
        when(adminCatalogService.listCategories()).thenReturn(
                List.of(category())
        );
    }

    @Test
    void shouldCreateTemplateWithDataAndInstructionSheets() throws Exception {
        byte[] content = service.createTemplate();

        assertThat(content).startsWith((byte) 'P', (byte) 'K');
        try (Workbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(content)
        )) {
            assertThat(workbook.getSheet("商品数据")).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.getSheet("商品数据")
                    .getRow(0)
                    .getCell(0)
                    .getStringCellValue()).isEqualTo("SKU编码");
        }
    }

    @Test
    void shouldImportValidBeadAndReportDuplicateExcelRow() throws Exception {
        when(validator.validate(any(CreateBeadSkuRequest.class)))
                .thenReturn(Set.of());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "catalog.xlsx",
                "application/vnd.openxmlformats-officedocument"
                        + ".spreadsheetml.sheet",
                importWorkbook()
        );

        CatalogImportResult result = service.importProducts(file);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.skuCode()).isEqualTo("BEAD-EXCEL-001");
            assertThat(error.message()).isEqualTo("Excel 内 SKU 编码重复");
        });
        ArgumentCaptor<CreateBeadSkuRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateBeadSkuRequest.class);
        verify(adminCatalogService).createBead(requestCaptor.capture());
        assertThat(requestCaptor.getValue().coverAssetId()).isNull();
        assertThat(requestCaptor.getValue().initialStock()).isEqualTo(77);
    }

    private Category category() {
        Category category = new Category();
        category.setId(1001L);
        category.setName("天然水晶");
        category.setSlug("natural-crystal");
        category.setSortOrder(10);
        category.setStatus(CatalogStatus.ON_SALE);
        return category;
    }

    private byte[] importWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("商品数据");
            writeRow(sheet.createRow(0), new String[]{
                    "SKU编码", "商品名称", "分类名称", "材质", "颜色",
                    "珠径(mm)", "销售价(元)", "成本价(元)", "初始库存",
                    "库存预警", "标签", "状态"
            });
            writeRow(sheet.createRow(1), new String[]{
                    "BEAD-EXCEL-001", "Excel 白水晶", "天然水晶", "水晶",
                    "透明", "8", "9.80", "2.10", "77", "10",
                    "日常，通勤", "已上架"
            });
            writeRow(sheet.createRow(2), new String[]{
                    "BEAD-EXCEL-001", "重复 SKU", "天然水晶", "水晶",
                    "透明", "8", "9.80", "2.10", "77", "10",
                    "重复", "草稿"
            });
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeRow(Row row, String[] values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
