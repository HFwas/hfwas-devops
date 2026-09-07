package com.hfwas.devops.image.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.image.dto.ImageBatchConvertRequest;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.dto.ImageConvertVO;
import com.hfwas.devops.image.dto.ImageHealthVO;
import com.hfwas.devops.image.dto.ImageHistoryVO;
import com.hfwas.devops.image.dto.ImageMetadataVO;
import com.hfwas.devops.image.dto.ImageSessionVO;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.ImageSessionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 图片处理 HTTP 入口，前缀 {@code /api/image}。
 * 会话在服务端临时目录，不落原图到历史表；转换结果通过 session 下载。
 */
@Slf4j
@RestController
@RequestMapping("/api/image")
public class ImageProcessorController {

    private final ImageSessionService sessionService;
    private final EngineProbe engineProbe;

    public ImageProcessorController(ImageSessionService sessionService, EngineProbe engineProbe) {
        this.sessionService = sessionService;
        this.engineProbe = engineProbe;
    }

    @GetMapping("/health")
    public BaseResult<ImageHealthVO> health() {
        return BaseResult.ok(engineProbe.health());
    }

    @GetMapping("/history")
    public BaseResult<List<ImageHistoryVO>> history(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return BaseResult.ok(sessionService.history(limit));
    }

    @PostMapping("/sessions")
    public BaseResult<ImageSessionVO> create(@RequestParam("file") MultipartFile file) {
        log.info("Image session upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
        return BaseResult.ok(sessionService.create(file));
    }

    @GetMapping("/sessions/{id}")
    public BaseResult<ImageSessionVO> get(@PathVariable("id") String id) {
        return BaseResult.ok(sessionService.get(id));
    }

    @GetMapping("/sessions/{id}/metadata")
    public BaseResult<ImageMetadataVO> metadata(@PathVariable("id") String id) {
        return BaseResult.ok(sessionService.metadata(id));
    }

    @GetMapping("/sessions/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable("id") String id) {
        Path file = sessionService.previewFile(id);
        MediaType mediaType = MediaType.parseMediaType(sessionService.previewMime(id));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(new FileSystemResource(file));
    }

    @PostMapping("/sessions/{id}/convert")
    public BaseResult<ImageConvertVO> convert(@PathVariable("id") String id,
                                              @Valid @RequestBody ImageConvertRequest request) {
        return BaseResult.ok(sessionService.convert(id, request));
    }

    @GetMapping("/sessions/{id}/jobs/{jobId}")
    public BaseResult<ImageConvertVO> job(@PathVariable("id") String id, @PathVariable("jobId") String jobId) {
        return BaseResult.ok(sessionService.job(id, jobId));
    }

    @PostMapping("/batch-convert")
    public BaseResult<List<ImageConvertVO>> batchConvert(@Valid @RequestBody ImageBatchConvertRequest request) {
        return BaseResult.ok(sessionService.batchConvert(request.getSessionIds(), request.getConvert()));
    }

    @GetMapping("/sessions/{id}/result")
    public ResponseEntity<Resource> result(@PathVariable("id") String id) {
        Path file = sessionService.resultFile(id);
        String name = sessionService.resultFileName(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(sessionService.resultMime(id)))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file));
    }

    @DeleteMapping("/sessions/{id}")
    public BaseResult<Void> delete(@PathVariable("id") String id) {
        sessionService.delete(id);
        return BaseResult.ok();
    }
}
