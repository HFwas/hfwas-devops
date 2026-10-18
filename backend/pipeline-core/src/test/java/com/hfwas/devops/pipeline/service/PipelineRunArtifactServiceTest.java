package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.pipeline.dto.PipelineRunArtifactVO;
import com.hfwas.devops.pipeline.entity.PipelineRunArtifactEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.mapper.PipelineRunArtifactMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-103..111、PIPE-157：运行产物落盘、SBOM 解析与下载。
 */
@ExtendWith(MockitoExtension.class)
class PipelineRunArtifactServiceTest {

    @Mock
    private PipelineRunArtifactMapper artifactMapper;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private SbomParserService sbomParserService;

    @TempDir
    Path tempDir;

    private PipelineRunArtifactService service;

    @BeforeEach
    void setUp() {
        service = new PipelineRunArtifactService(artifactMapper, runMapper, sbomParserService);
        ReflectionTestUtils.setField(service, "storageBase", tempDir.toString());
    }

    @Test
    @DisplayName("PIPE-103: 上传产物落盘并写入元数据")
    void uploadSavesFileAndMetadata() throws Exception {
        when(runMapper.selectById(99L)).thenReturn(run());
        when(artifactMapper.insert(any(PipelineRunArtifactEntity.class))).thenAnswer(inv -> {
            inv.<PipelineRunArtifactEntity>getArgument(0).setId(5L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile("file", "report.json",
                "application/json", "{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        PipelineRunArtifactVO vo = service.upload(99L, 21L, "file", file);

        assertThat(vo.getId()).isEqualTo(5L);
        assertThat(vo.getFileName()).isEqualTo("report.json");
        ArgumentCaptor<PipelineRunArtifactEntity> captor = ArgumentCaptor.forClass(PipelineRunArtifactEntity.class);
        verify(artifactMapper).insert(captor.capture());
        Path stored = storedPath(captor.getValue().getStoragePath());
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readString(stored)).isEqualTo("{\"a\":1}");
        assertThat(captor.getValue().getArtifactType()).isEqualTo("file");
    }

    @Test
    @DisplayName("PIPE-106: 未指定类型默认 sbom、缺省文件名与 Content-Type")
    void uploadAppliesDefaults() {
        when(runMapper.selectById(99L)).thenReturn(run());
        when(artifactMapper.insert(any(PipelineRunArtifactEntity.class))).thenAnswer(inv -> {
            inv.<PipelineRunArtifactEntity>getArgument(0).setId(5L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile("file", "", null, new byte[]{1});

        PipelineRunArtifactVO vo = service.upload(99L, null, null, file);

        assertThat(vo.getArtifactType()).isEqualTo("sbom");
        assertThat(vo.getFileName()).isEqualTo("sbom.json");
    }

    @Test
    @DisplayName("PIPE-105: run 不存在返回 10003")
    void uploadRejectsMissingRun() {
        when(runMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.upload(99L, null, "file", new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1})));

        assertThat(ex.getMessage()).isEqualTo("运行记录不存在");
        verify(artifactMapper, never()).insert(any(PipelineRunArtifactEntity.class));
    }

    @Test
    @DisplayName("PIPE-107: sbom 产物触发组件解析")
    void uploadSbomTriggersParser() {
        when(runMapper.selectById(99L)).thenReturn(run());
        when(artifactMapper.insert(any(PipelineRunArtifactEntity.class))).thenAnswer(inv -> {
            inv.<PipelineRunArtifactEntity>getArgument(0).setId(5L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile("file", "bom.json", "application/json", "{}".getBytes());

        service.upload(99L, 21L, "sbom", file);

        verify(sbomParserService).parse(eq(5L), eq(99L), any(InputStream.class));
    }

    @Test
    @DisplayName("PIPE-108: 普通产物不触发 SBOM 解析")
    void uploadNonSbomSkipsParser() {
        when(runMapper.selectById(99L)).thenReturn(run());
        when(artifactMapper.insert(any(PipelineRunArtifactEntity.class))).thenAnswer(inv -> {
            inv.<PipelineRunArtifactEntity>getArgument(0).setId(5L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile("file", "app.log", "text/plain", "log".getBytes());

        service.upload(99L, null, "file", file);

        verify(sbomParserService, never()).parse(anyLong(), anyLong(), any(InputStream.class));
    }

    @Test
    @DisplayName("PIPE-109: 产物列表按 run 查询")
    void listByRunReturnsArtifacts() {
        when(runMapper.selectById(99L)).thenReturn(run());
        PipelineRunArtifactEntity entity = artifact(5L, "sbom.json");
        when(artifactMapper.selectList(any())).thenReturn(List.of(entity));

        List<PipelineRunArtifactVO> rows = service.listByRun(99L);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getFileName()).isEqualTo("sbom.json");
    }

    @Test
    @DisplayName("PIPE-105: 列表查询 run 不存在返回 10003")
    void listByRunRejectsMissingRun() {
        when(runMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.listByRun(99L));

        assertThat(ex.getMessage()).isEqualTo("运行记录不存在");
    }

    @Test
    @DisplayName("PIPE-109: 产物不存在返回 10003")
    void getByIdRejectsMissing() {
        when(artifactMapper.selectById(5L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getById(5L));

        assertThat(ex.getMessage()).isEqualTo("产物不存在");
    }

    @Test
    @DisplayName("PIPE-110: 查询最新 SBOM")
    void getSbomByRunQueriesLatest() {
        PipelineRunArtifactEntity entity = artifact(5L, "sbom.json");
        when(artifactMapper.selectOne(any())).thenReturn(entity);

        assertThat(service.getSbomByRun(99L).getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("PIPE-109: 下载流读取落盘文件")
    void openStreamReadsStoredFile() throws Exception {
        Path file = tempDir.resolve("99/abc/report.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hello");
        PipelineRunArtifactEntity entity = artifact(5L, "report.txt");
        entity.setStoragePath("99/abc/report.txt");

        try (InputStream in = service.openStream(entity)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    @DisplayName("PIPE-109: 文件缺失时下载返回 10003")
    void openStreamRejectsMissingFile() {
        PipelineRunArtifactEntity entity = artifact(5L, "missing.txt");
        entity.setStoragePath("99/abc/missing.txt");

        BizException ex = assertThrows(BizException.class, () -> service.openStream(entity));

        assertThat(ex.getMessage()).contains("产物文件不存在");
    }

    @Test
    @DisplayName("PIPE-111: 跨租户访问产物应被拒绝")
    @Disabled("PIPE-916: 产物 list/download/sbom 未校验 run 所属租户")
    void artifactsShouldRejectCrossTenantAccess() {
        org.junit.jupiter.api.Assertions.fail("PIPE-916: 需在产物查询中校验租户与 run 归属");
    }

    @Test
    @DisplayName("PIPE-157: 文件名中的 ../ 不应逃逸存储目录（当前实现未清洗）")
    @Disabled("PIPE-917: upload 直接使用 getOriginalFilename() 拼接路径，未做路径穿越清洗")
    void uploadShouldSanitizePathTraversalFileName() {
        when(runMapper.selectById(99L)).thenReturn(run());
        when(artifactMapper.insert(any(PipelineRunArtifactEntity.class))).thenAnswer(inv -> {
            inv.<PipelineRunArtifactEntity>getArgument(0).setId(5L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile("file", "../../../evil.txt", "text/plain", "x".getBytes());

        service.upload(99L, null, "file", file);

        assertThat(Files.exists(tempDir.getParent().resolve("evil.txt"))).isFalse();
    }

    private Path storedPath(String storagePath) {
        return tempDir.resolve(storagePath);
    }

    private static PipelineRunEntity run() {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(99L);
        run.setPipelineId(1L);
        return run;
    }

    private static PipelineRunArtifactEntity artifact(Long id, String fileName) {
        PipelineRunArtifactEntity entity = new PipelineRunArtifactEntity();
        entity.setId(id);
        entity.setRunId(99L);
        entity.setFileName(fileName);
        entity.setFileSize(5L);
        entity.setContentType("text/plain");
        entity.setStoragePath("99/abc/" + fileName);
        return entity;
    }
}
