package com.hfwas.devops.pm.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.WorkItemExportRequest;
import com.hfwas.devops.pm.workitem.io.*;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/pm/work-items/io")
@RequiredArgsConstructor
public class PmWorkItemImportExportController {

    private final WorkItemImportExportService importExportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/columns")
    public BaseResult<List<WorkItemIoColumn>> columns(
            @RequestParam Long projectId,
            @RequestParam String typeCode) {
        return BaseResult.ok(importExportService.listColumns(projectId, typeCode));
    }

    @PostMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate(@RequestBody WorkItemExportRequest request) {
        byte[] data = importExportService.exportImportTemplate(
                request.getProjectId(),
                request.getTypeCode(),
                request.getFieldKeys());
        String filename = importExportService.importTemplateFilename(request.getProjectId(), request.getTypeCode());
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @OperLog(module = "pm", action = "export", bizType = "work_item", summary = "导出事项 Excel")
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody WorkItemExportRequest request) {
        byte[] data = importExportService.exportExcel(
                request.getProjectId(),
                request.getTypeCode(),
                request.getIds(),
                request.getQuerySpec(),
                request.getFieldKeys());
        String filename = importExportService.exportFilename(request.getProjectId(), request.getTypeCode());
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/import/preview")
    public BaseResult<WorkItemImportPreview> previewImport(
            @RequestParam Long projectId,
            @RequestParam String typeCode,
            @RequestParam("file") MultipartFile file) {
        return BaseResult.ok(importExportService.previewImport(projectId, typeCode, readBytes(file)));
    }

    @OperLog(module = "pm", action = "import", bizType = "work_item", summary = "导入事项 Excel")
    @PostMapping("/import")
    public BaseResult<WorkItemImportResult> importExcel(
            @RequestParam Long projectId,
            @RequestParam String typeCode,
            @RequestParam(defaultValue = "CREATE") WorkItemImportMode mode,
            @RequestParam(value = "fieldKeys", required = false) String fieldKeysJson,
            @RequestParam("file") MultipartFile file) {
        List<String> fieldKeys = parseFieldKeys(fieldKeysJson);
        return BaseResult.ok(importExportService.importExcel(
                projectId, typeCode, readBytes(file), mode, fieldKeys));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 Excel 文件");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败");
        }
    }

    private List<String> parseFieldKeys(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("fieldKeys 格式错误");
        }
    }
}
