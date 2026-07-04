package com.hfwas.devops.pm.workitem.io;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.mapper.PmProjectMapper;
import com.hfwas.devops.pm.query.engine.QueryEngine;
import com.hfwas.devops.pm.query.model.QuerySpec;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.service.WorkItemKeyEnricher;
import com.hfwas.devops.pm.workitem.service.WorkItemService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkItemImportExportService {

    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_IMPORT_ROWS = 5_000;
    private static final String DATA_SHEET = "数据";
    private static final String META_SHEET = "字段映射";

    private final WorkItemIoFieldRegistry fieldRegistry;
    private final WorkItemIoValueCodec valueCodec;
    private final WorkItemService workItemService;
    private final PmWorkItemMapper workItemMapper;
    private final WorkItemKeyEnricher keyEnricher;
    private final QueryEngine queryEngine;
    private final PmProjectMapper projectMapper;

    public List<WorkItemIoColumn> listColumns(Long projectId, String typeCode) {
        validateScope(projectId, typeCode);
        return fieldRegistry.listColumns(projectId, typeCode);
    }

    public byte[] exportExcel(Long projectId, String typeCode, List<String> ids, QuerySpec querySpec,
                              List<String> fieldKeys) {
        validateScope(projectId, typeCode);
        List<WorkItemIoColumn> allColumns = fieldRegistry.listColumns(projectId, typeCode);
        List<WorkItemIoColumn> columns = resolveExportColumns(allColumns, fieldKeys);
        List<PmWorkItem> items = loadExportItems(projectId, typeCode, ids, querySpec);
        Map<String, FieldDefinition> defMap = fieldRegistry.fieldDefinitionMap(projectId, typeCode);

        List<List<Object>> rows = new ArrayList<>();
        for (PmWorkItem item : items) {
            List<Object> row = new ArrayList<>();
            for (WorkItemIoColumn col : columns) {
                row.add(valueCodec.exportValue(item, col, defMap.get(col.getFieldKey())));
            }
            rows.add(row);
        }
        return writeWorkbook(columns, rows);
    }

    public byte[] exportImportTemplate(Long projectId, String typeCode, List<String> fieldKeys) {
        validateScope(projectId, typeCode);
        List<WorkItemIoColumn> columns = resolveImportColumns(fieldRegistry.listColumns(projectId, typeCode), fieldKeys);
        List<List<Object>> sampleRow = List.of(columns.stream().map(c -> (Object) "").toList());
        return writeWorkbook(columns, sampleRow);
    }

    public String importTemplateFilename(Long projectId, String typeCode) {
        PmProject project = projectMapper.selectById(projectId);
        String code = project != null && StringUtils.isNotBlank(project.getCode()) ? project.getCode() : "project";
        return code + "-" + typeCode + "-import-template.xlsx";
    }

    private byte[] writeWorkbook(List<WorkItemIoColumn> columns, List<List<Object>> dataRows) {
        List<List<String>> head = columns.stream().map(c -> List.of(c.getFieldName())).toList();

        List<List<String>> metaHead = List.of(
                List.of("列序号"), List.of("字段编码"), List.of("字段名称"));
        List<List<Object>> metaRows = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            WorkItemIoColumn col = columns.get(i);
            metaRows.add(List.of(String.valueOf(i), col.getFieldKey(), col.getFieldName()));
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var writer = EasyExcel.write(out).build();
            var dataSheet = EasyExcel.writerSheet(0, DATA_SHEET).head(head).build();
            writer.write(dataRows, dataSheet);
            var metaSheet = EasyExcel.writerSheet(1, META_SHEET).head(metaHead).build();
            writer.write(metaRows, metaSheet);
            writer.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }

    public WorkItemImportPreview previewImport(Long projectId, String typeCode, byte[] fileBytes) {
        validateScope(projectId, typeCode);
        ParsedSheet parsed = parseSheet(fileBytes);
        WorkItemImportPreview preview = new WorkItemImportPreview();
        preview.setTotalRows(parsed.dataRows.size());
        preview.setDetectedHeaders(parsed.headers);
        ColumnMapping mapping = buildColumnMapping(projectId, typeCode, parsed);
        preview.getWarnings().addAll(mapping.warnings);

        int valid = 0;
        for (int i = 0; i < parsed.dataRows.size(); i++) {
            Map<String, String> rowMap = toRowMap(parsed.dataRows.get(i), mapping);
            if (StringUtils.isNotBlank(rowMap.get("title")) || StringUtils.isNotBlank(rowMap.get("itemKey"))) {
                valid++;
            }
            if (preview.getSampleRows().size() < 5) {
                preview.getSampleRows().add(rowMap);
            }
        }
        preview.setValidRows(valid);
        if (parsed.dataRows.size() > MAX_IMPORT_ROWS) {
            preview.getWarnings().add("超过最大导入行数 " + MAX_IMPORT_ROWS + "，仅处理前 " + MAX_IMPORT_ROWS + " 行");
        }
        return preview;
    }

    @Transactional
    public WorkItemImportResult importExcel(Long projectId, String typeCode, byte[] fileBytes,
                                            WorkItemImportMode mode, List<String> fieldKeys) {
        validateScope(projectId, typeCode);
        ParsedSheet parsed = parseSheet(fileBytes);
        if (parsed.dataRows.isEmpty()) {
            throw new IllegalArgumentException("Excel 中没有数据行");
        }
        ColumnMapping mapping = buildColumnMapping(projectId, typeCode, parsed);
        if (fieldKeys != null && !fieldKeys.isEmpty()) {
            mapping = mapping.filterFields(new LinkedHashSet<>(fieldKeys));
        }
        Map<String, FieldDefinition> defMap = fieldRegistry.fieldDefinitionMap(projectId, typeCode);
        Map<String, WorkItemIoColumn> columnMap = fieldRegistry.columnMap(projectId, typeCode);

        WorkItemImportResult result = new WorkItemImportResult();
        result.getWarnings().addAll(mapping.warnings);
        int limit = Math.min(parsed.dataRows.size(), MAX_IMPORT_ROWS);

        for (int i = 0; i < limit; i++) {
            Map<String, String> raw = toRowMap(parsed.dataRows.get(i), mapping);
            if (raw.values().stream().allMatch(StringUtils::isBlank)) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            try {
                boolean updated = importOneRow(projectId, typeCode, mode, raw, columnMap, defMap);
                if (updated) {
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    result.setCreated(result.getCreated() + 1);
                }
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add("第 " + (i + 2) + " 行: " + e.getMessage());
            }
        }
        return result;
    }

    public String exportFilename(Long projectId, String typeCode) {
        PmProject project = projectMapper.selectById(projectId);
        String code = project != null && StringUtils.isNotBlank(project.getCode()) ? project.getCode() : "project";
        return code + "-" + typeCode + "-export.xlsx";
    }

    private boolean importOneRow(Long projectId, String typeCode, WorkItemImportMode mode,
                                 Map<String, String> raw, Map<String, WorkItemIoColumn> columnMap,
                                 Map<String, FieldDefinition> defMap) {
        String itemKey = raw.get(WorkItemIoFieldRegistry.ITEM_KEY);
        PmWorkItem item = null;
        if (mode == WorkItemImportMode.UPSERT && StringUtils.isNotBlank(itemKey)) {
            item = findByItemKey(projectId, typeCode, itemKey.trim());
        }
        boolean isUpdate = item != null;
        if (item == null) {
            item = new PmWorkItem();
            item.setProjectId(projectId);
            item.setTypeCode(typeCode);
        }
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String fieldKey = entry.getKey();
            if (WorkItemIoFieldRegistry.ITEM_KEY.equals(fieldKey)) {
                continue;
            }
            WorkItemIoColumn column = columnMap.get(fieldKey);
            if (column == null || !column.isImportable()) {
                continue;
            }
            FieldDefinition def = defMap.get(fieldKey);
            Object value = valueCodec.importValue(entry.getValue(), column, def, projectId, typeCode);
            valueCodec.applyValue(item, column, def, value);
        }
        if (StringUtils.isBlank(item.getTitle())) {
            throw new IllegalArgumentException("标题不能为空");
        }
        workItemService.save(item);
        return isUpdate;
    }

    private PmWorkItem findByItemKey(Long projectId, String typeCode, String itemKey) {
        Integer itemNo = parseItemNo(itemKey);
        if (itemNo == null) {
            return null;
        }
        PmWorkItem item = workItemMapper.selectOne(Wrappers.<PmWorkItem>lambdaQuery()
                .eq(PmWorkItem::getProjectId, projectId)
                .eq(PmWorkItem::getTypeCode, typeCode)
                .eq(PmWorkItem::getItemNo, itemNo)
                .last("LIMIT 1"));
        if (item != null) {
            keyEnricher.enrich(item);
        }
        return item;
    }

    private Integer parseItemNo(String itemKey) {
        if (StringUtils.isBlank(itemKey)) {
            return null;
        }
        int idx = itemKey.lastIndexOf('-');
        if (idx < 0 || idx >= itemKey.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(itemKey.substring(idx + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<PmWorkItem> loadExportItems(Long projectId, String typeCode, List<String> ids, QuerySpec querySpec) {
        List<PmWorkItem> items;
        if (ids != null && !ids.isEmpty()) {
            List<Long> longIds = ids.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
            items = workItemMapper.selectList(Wrappers.<PmWorkItem>lambdaQuery()
                    .eq(PmWorkItem::getProjectId, projectId)
                    .eq(PmWorkItem::getTypeCode, typeCode)
                    .in(PmWorkItem::getId, longIds)
                    .orderByDesc(PmWorkItem::getUpdateTime));
        } else {
            QuerySpec spec = querySpec != null ? querySpec : new QuerySpec();
            spec.setProjectId(projectId);
            spec.setTypeCode(typeCode);
            spec.setPageNo(1);
            spec.setPageSize(MAX_EXPORT_ROWS);
            items = queryEngine.execute(spec).getRecords();
        }
        keyEnricher.enrich(items);
        if (items.size() >= MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("导出数据超过 " + MAX_EXPORT_ROWS + " 条，请缩小筛选范围");
        }
        return items;
    }

    private List<WorkItemIoColumn> resolveExportColumns(List<WorkItemIoColumn> all, List<String> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return all.stream().filter(WorkItemIoColumn::isDefaultSelected).toList();
        }
        Map<String, WorkItemIoColumn> map = all.stream()
                .collect(Collectors.toMap(WorkItemIoColumn::getFieldKey, c -> c, (a, b) -> a, LinkedHashMap::new));
        List<WorkItemIoColumn> selected = new ArrayList<>();
        for (String key : fieldKeys) {
            WorkItemIoColumn col = map.get(key);
            if (col != null && col.isExportable()) {
                selected.add(col);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个导出字段");
        }
        return selected;
    }

    private List<WorkItemIoColumn> resolveImportColumns(List<WorkItemIoColumn> all, List<String> fieldKeys) {
        List<WorkItemIoColumn> importable = all.stream().filter(WorkItemIoColumn::isImportable).toList();
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return importable.stream().filter(c -> !WorkItemIoFieldRegistry.ITEM_KEY.equals(c.getFieldKey())).toList();
        }
        Map<String, WorkItemIoColumn> map = all.stream()
                .collect(Collectors.toMap(WorkItemIoColumn::getFieldKey, c -> c, (a, b) -> a, LinkedHashMap::new));
        List<WorkItemIoColumn> selected = new ArrayList<>();
        for (String key : fieldKeys) {
            WorkItemIoColumn col = map.get(key);
            if (col != null && col.isImportable()) {
                selected.add(col);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个导入字段");
        }
        return selected;
    }

    private ColumnMapping buildColumnMapping(Long projectId, String typeCode, ParsedSheet parsed) {
        Map<String, WorkItemIoColumn> columnsByKey = fieldRegistry.columnMap(projectId, typeCode);
        Map<String, WorkItemIoColumn> columnsByName = columnsByKey.values().stream()
                .collect(Collectors.toMap(WorkItemIoColumn::getFieldName, c -> c, (a, b) -> a));

        List<String> warnings = new ArrayList<>();
        List<String> fieldKeys = new ArrayList<>();

        if (!parsed.metaFieldKeys.isEmpty()) {
            for (String key : parsed.metaFieldKeys) {
                if (columnsByKey.containsKey(key)) {
                    fieldKeys.add(key);
                } else {
                    warnings.add("忽略未知字段编码: " + key);
                }
            }
        } else {
            for (String header : parsed.headers) {
                if (StringUtils.isBlank(header)) {
                    continue;
                }
                WorkItemIoColumn byName = columnsByName.get(header.trim());
                WorkItemIoColumn byKey = columnsByKey.get(header.trim());
                WorkItemIoColumn col = byName != null ? byName : byKey;
                if (col != null) {
                    fieldKeys.add(col.getFieldKey());
                } else {
                    warnings.add("无法识别列: " + header);
                    fieldKeys.add(null);
                }
            }
        }
        return new ColumnMapping(fieldKeys, warnings);
    }

    private Map<String, String> toRowMap(List<String> row, ColumnMapping mapping) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < mapping.fieldKeys.size(); i++) {
            String key = mapping.fieldKeys.get(i);
            if (key == null) {
                continue;
            }
            String val = i < row.size() && row.get(i) != null ? row.get(i).trim() : "";
            map.put(key, val);
        }
        return map;
    }

    private ParsedSheet parseSheet(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("请上传 Excel 文件");
        }
        ParsedSheet parsed = new ParsedSheet();
        readDataSheet(bytes, parsed);
        readMetaSheet(bytes, parsed);
        return parsed;
    }

    private void readDataSheet(byte[] bytes, ParsedSheet parsed) {
        EasyExcel.read(new java.io.ByteArrayInputStream(bytes), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                parsed.headers = headMap.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getValue() != null ? e.getValue().trim() : "")
                        .toList();
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                List<String> row = data.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getValue() != null ? e.getValue().trim() : "")
                        .toList();
                if (row.stream().anyMatch(StringUtils::isNotBlank)) {
                    parsed.dataRows.add(row);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet(0).headRowNumber(1).doRead();
    }

    private void readMetaSheet(byte[] bytes, ParsedSheet parsed) {
        try {
            EasyExcel.read(new java.io.ByteArrayInputStream(bytes), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    String fieldKey = data.get(1);
                    if (StringUtils.isNotBlank(fieldKey)) {
                        parsed.metaFieldKeys.add(fieldKey.trim());
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet(META_SHEET).headRowNumber(1).doRead();
        } catch (Exception ignored) {
            // meta sheet optional for files edited externally
        }
    }

    private void validateScope(Long projectId, String typeCode) {
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
    }

    private static class ParsedSheet {
        List<String> headers = new ArrayList<>();
        List<String> metaFieldKeys = new ArrayList<>();
        List<List<String>> dataRows = new ArrayList<>();
    }

    private record ColumnMapping(List<String> fieldKeys, List<String> warnings) {
        ColumnMapping filterFields(Set<String> allowed) {
            List<String> filtered = fieldKeys.stream()
                    .filter(k -> k != null && allowed.contains(k))
                    .toList();
            return new ColumnMapping(filtered, warnings);
        }
    }
}
