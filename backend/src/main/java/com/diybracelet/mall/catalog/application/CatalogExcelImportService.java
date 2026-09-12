package com.diybracelet.mall.catalog.application;

import com.diybracelet.mall.catalog.api.dto.CatalogImportError;
import com.diybracelet.mall.catalog.api.dto.CatalogImportResult;
import com.diybracelet.mall.catalog.api.dto.CreateBeadSkuRequest;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.Category;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台商品 Excel 模板与批量导入服务。
 *
 * <p>模板不承载图片；导入后由商品编辑页补充封面。每一行通过既有创建服务独立落库，
 * 因此合法行不会因为其他行填写错误而回滚。</p>
 */
@Service
public class CatalogExcelImportService {

    public static final String TEMPLATE_FILE_NAME = "商品批量导入模板.xlsx";
    private static final String DATA_SHEET_NAME = "商品数据";
    private static final int MAX_IMPORT_ROWS = 1000;
    private static final long MAX_FILE_SIZE_BYTES = 10_000_000L;
    private static final List<String> HEADERS = List.of(
            "SKU编码",
            "商品名称",
            "分类名称",
            "材质",
            "颜色",
            "珠径(mm)",
            "销售价(元)",
            "成本价(元)",
            "初始库存",
            "库存预警",
            "标签",
            "状态"
    );

    private final AdminCatalogService adminCatalogService;
    private final Validator validator;

    public CatalogExcelImportService(
            AdminCatalogService adminCatalogService,
            Validator validator
    ) {
        this.adminCatalogService = adminCatalogService;
        this.validator = validator;
    }

    /** 生成包含当前分类下拉项和填写说明的 xlsx 模板。 */
    public byte[] createTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<Category> categories = adminCatalogService.listCategories();
            createDataSheet(workbook, categories);
            createInstructionSheet(workbook, categories);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("商品导入模板生成失败", exception);
        }
    }

    /** 解析 xlsx 并逐行创建商品，返回可定位到 Excel 行号的结果。 */
    public CatalogImportResult importProducts(MultipartFile file) {
        validateFile(file);
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(DATA_SHEET_NAME);
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() == 0
                        ? null
                        : workbook.getSheetAt(0);
            }
            if (sheet == null) {
                throw badRequest("Excel 中没有商品数据工作表");
            }
            Map<String, Integer> columns = readAndValidateHeader(sheet);
            validateRowLimit(sheet, columns);
            Map<String, Category> categories = loadCategoriesByName();
            return importRows(sheet, columns, categories);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | POIXMLException exception) {
            throw badRequest("Excel 文件无法读取，请使用下载的 .xlsx 模板");
        }
    }

    private void validateRowLimit(Sheet sheet, Map<String, Integer> columns) {
        int count = 0;
        for (Row row : sheet) {
            if (row.getRowNum() > 0 && !isBlankRow(row, columns)
                    && ++count > MAX_IMPORT_ROWS) {
                throw badRequest("单次最多导入 " + MAX_IMPORT_ROWS + " 行商品");
            }
        }
    }

    private CatalogImportResult importRows(
            Sheet sheet,
            Map<String, Integer> columns,
            Map<String, Category> categories
    ) {
        List<CatalogImportError> errors = new ArrayList<>();
        Set<String> importedSkuCodes = new HashSet<>();
        int totalRows = 0;
        int successCount = 0;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, columns)) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_IMPORT_ROWS) {
                throw badRequest("单次最多导入 " + MAX_IMPORT_ROWS + " 行商品");
            }
            String skuCode = text(row, columns.get("SKU编码"))
                    .toUpperCase(Locale.ROOT);
            try {
                if (!skuCode.isBlank() && !importedSkuCodes.add(skuCode)) {
                    throw new IllegalArgumentException("Excel 内 SKU 编码重复");
                }
                CreateBeadSkuRequest request = toRequest(
                        row,
                        columns,
                        categories,
                        skuCode
                );
                validateRequest(request);
                adminCatalogService.createBead(request);
                successCount++;
            } catch (BusinessException | IllegalArgumentException exception) {
                errors.add(new CatalogImportError(
                        rowIndex + 1,
                        skuCode,
                        exception.getMessage()
                ));
            } catch (DataIntegrityViolationException exception) {
                errors.add(new CatalogImportError(
                        rowIndex + 1,
                        skuCode,
                        "数据与现有记录冲突"
                ));
            }
        }
        if (totalRows == 0) {
            throw badRequest("Excel 中没有可导入的商品数据");
        }
        return new CatalogImportResult(
                totalRows,
                successCount,
                errors.size(),
                List.copyOf(errors)
        );
    }

    private CreateBeadSkuRequest toRequest(
            Row row,
            Map<String, Integer> columns,
            Map<String, Category> categories,
            String skuCode
    ) {
        String categoryName = text(row, columns.get("分类名称"));
        Category category = categories.get(normalize(categoryName));
        if (category == null) {
            throw new IllegalArgumentException("分类不存在：" + categoryName);
        }
        return new CreateBeadSkuRequest(
                category.getId(),
                skuCode,
                text(row, columns.get("商品名称")),
                text(row, columns.get("材质")),
                text(row, columns.get("颜色")),
                optionalDecimal(row, columns.get("珠径(mm)"), "珠径"),
                requiredDecimal(row, columns.get("销售价(元)"), "销售价"),
                requiredDecimal(row, columns.get("成本价(元)"), "成本价"),
                requiredInteger(row, columns.get("库存预警"), "库存预警"),
                null,
                parseTags(text(row, columns.get("标签"))),
                parseStatus(text(row, columns.get("状态"))),
                requiredInteger(row, columns.get("初始库存"), "初始库存")
        );
    }

    private void validateRequest(CreateBeadSkuRequest request) {
        Set<ConstraintViolation<CreateBeadSkuRequest>> violations =
                validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .sorted(Comparator.comparing(
                        violation -> violation.getPropertyPath().toString()
                ))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("商品数据不合法");
        throw new IllegalArgumentException(message);
    }

    private Map<String, Category> loadCategoriesByName() {
        Map<String, Category> result = new HashMap<>();
        for (Category category : adminCatalogService.listCategories()) {
            result.put(normalize(category.getName()), category);
            result.put(normalize(category.getSlug()), category);
        }
        return result;
    }

    private Map<String, Integer> readAndValidateHeader(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw badRequest("Excel 缺少表头");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim();
            if (!value.isBlank()) {
                columns.put(value, cell.getColumnIndex());
            }
        }
        List<String> missing = HEADERS.stream()
                .filter(header -> !columns.containsKey(header))
                .toList();
        if (!missing.isEmpty()) {
            throw badRequest("Excel 缺少列：" + String.join("、", missing));
        }
        return columns;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择需要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw badRequest("Excel 文件不能超过 10 MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw badRequest("仅支持 .xlsx 格式的 Excel 文件");
        }
    }

    private boolean isBlankRow(Row row, Map<String, Integer> columns) {
        return HEADERS.stream().allMatch(
                header -> text(row, columns.get(header)).isBlank()
        );
    }

    private String text(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return new DataFormatter().formatCellValue(cell).trim();
    }

    private BigDecimal requiredDecimal(
            Row row,
            int columnIndex,
            String fieldName
    ) {
        BigDecimal value = optionalDecimal(row, columnIndex, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private BigDecimal optionalDecimal(
            Row row,
            int columnIndex,
            String fieldName
    ) {
        String value = text(row, columnIndex);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + "必须是数字");
        }
    }

    private int requiredInteger(Row row, int columnIndex, String fieldName) {
        BigDecimal value = requiredDecimal(row, columnIndex, fieldName);
        try {
            return value.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + "必须是整数");
        }
    }

    private List<String> parseTags(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("[,，]"))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private CatalogStatus parseStatus(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "草稿", "DRAFT" -> CatalogStatus.DRAFT;
            case "已上架", "ON_SALE" -> CatalogStatus.ON_SALE;
            case "已下架", "OFF_SALE" -> CatalogStatus.OFF_SALE;
            default -> throw new IllegalArgumentException(
                    "状态只能填写草稿、已上架或已下架"
            );
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void createDataSheet(Workbook workbook, List<Category> categories) {
        Sheet sheet = workbook.createSheet(DATA_SHEET_NAME);
        Row header = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = header.createCell(index, CellType.STRING);
            cell.setCellValue(HEADERS.get(index));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, columnWidth(index));
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0,
                0,
                0,
                HEADERS.size() - 1
        ));
        addListValidation(
                sheet,
                2,
                categories.stream().map(Category::getName).toArray(String[]::new)
        );
        addListValidation(sheet, 11, new String[]{"草稿", "已上架", "已下架"});
    }

    private void createInstructionSheet(
            Workbook workbook,
            List<Category> categories
    ) {
        Sheet sheet = workbook.createSheet("填写说明");
        String[] instructions = {
                "商品批量导入填写说明",
                "1. 只支持 .xlsx 文件，单次最多 1000 行、10 MB。",
                "2. 图片不在 Excel 中导入；商品创建后可在后台编辑并上传封面。",
                "3. 珠子商品必须填写珠径；绳类和其他饰品请将珠径留空。",
                "4. SKU 编码只能使用大写字母、数字和连字符，且不能与已有商品重复。",
                "5. 标签使用中文或英文逗号分隔；状态填写草稿、已上架或已下架。",
                "6. 当前可用分类：" + categories.stream()
                        .map(Category::getName)
                        .reduce((left, right) -> left + "、" + right)
                        .orElse("暂无分类")
        };
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        for (int index = 0; index < instructions.length; index++) {
            Cell cell = sheet.createRow(index).createCell(0);
            cell.setCellValue(instructions[index]);
            if (index == 0) {
                cell.setCellStyle(titleStyle);
            }
        }
        sheet.setColumnWidth(0, 90 * 256);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private void addListValidation(
            Sheet sheet,
            int columnIndex,
            String[] values
    ) {
        if (values.length == 0) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                helper.createExplicitListConstraint(values);
        CellRangeAddressList range = new CellRangeAddressList(
                1,
                MAX_IMPORT_ROWS,
                columnIndex,
                columnIndex
        );
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private int columnWidth(int index) {
        return switch (index) {
            case 0 -> 28 * 256;
            case 1 -> 24 * 256;
            case 2, 3, 4, 10 -> 18 * 256;
            default -> 14 * 256;
        };
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.COMMON_BAD_REQUEST, message);
    }
}
