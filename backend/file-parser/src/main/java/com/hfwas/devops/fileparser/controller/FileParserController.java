package com.hfwas.devops.fileparser.controller;

import com.hfwas.devops.fileparser.dto.FileParseRequest;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.service.FileParserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件解析控制器
 * 提供文件上传解析 REST API。
 */
@Slf4j
@RestController
@RequestMapping("/api/file-parser")
public class FileParserController {

    private final FileParserService fileParserService;

    public FileParserController(FileParserService fileParserService) {
        this.fileParserService = fileParserService;
    }

    /**
     * 上传并解析单个文件
     */
    @PostMapping("/upload")
    public ResponseEntity<FileParseResultVO> upload(
            @RequestParam("file") MultipartFile file,
            @Valid FileParseRequest request) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    FileParseResultVO.builder()
                            .success(false)
                            .fileName(file.getOriginalFilename())
                            .errorMessage("上传文件为空")
                            .build()
            );
        }

        log.info("Received file upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        FileParseResultVO result = fileParserService.parse(file,
                request != null ? request.getOptions() : null);

        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}