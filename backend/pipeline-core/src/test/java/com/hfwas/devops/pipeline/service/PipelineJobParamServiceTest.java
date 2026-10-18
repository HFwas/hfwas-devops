package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.pipeline.dto.JobParamDefinitionVO;
import com.hfwas.devops.pipeline.dto.JobParamPreviewResultVO;
import com.hfwas.devops.pipeline.dto.JobParamSaveDTO;
import com.hfwas.devops.pipeline.entity.PipelineJobParamEntity;
import com.hfwas.devops.pipeline.mapper.PipelineJobParamMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-027、032、033、050、089、090、915：任务参数定义与远程预览。
 */
@ExtendWith(MockitoExtension.class)
class PipelineJobParamServiceTest {

    @Mock
    private PipelineJobParamMapper mapper;

    private PipelineJobParamService service;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        service = new PipelineJobParamService(mapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("PIPE-032: 创建参数回填 id 并默认 input/runtime")
    void createDefaultsAndBackfillsId() {
        when(mapper.insert(any(PipelineJobParamEntity.class))).thenAnswer(inv -> {
            inv.<PipelineJobParamEntity>getArgument(0).setId(5L);
            return 1;
        });
        JobParamSaveDTO dto = dto("PROFILE", null, "dev");
        dto.setValueMode(null);

        assertThat(service.create(dto)).isEqualTo(5L);

        ArgumentCaptor<PipelineJobParamEntity> captor = ArgumentCaptor.forClass(PipelineJobParamEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getParamType()).isEqualTo("input");
        assertThat(captor.getValue().getValueMode()).isEqualTo("runtime");
        assertThat(captor.getValue().getDefaultValue()).isEqualTo("dev");
        assertThat(captor.getValue().getApiMethod()).isEqualTo("GET");
        assertThat(captor.getValue().getRequired()).isZero();
    }

    @Test
    @DisplayName("PIPE-027: fixed 绑定保留 fixed 语义")
    void createKeepsFixedMode() {
        JobParamSaveDTO dto = dto("VERSION", "input", "1.0");
        dto.setValueMode(" FIXED ");

        service.create(dto);

        ArgumentCaptor<PipelineJobParamEntity> captor = ArgumentCaptor.forClass(PipelineJobParamEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getValueMode()).isEqualTo("fixed");
    }

    @Test
    @DisplayName("PIPE-032: 更新不存在参数返回 10003")
    void updateRejectsMissing() {
        when(mapper.selectById(5L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(5L, dto("X", "input", "1")));

        assertThat(ex.getMessage()).isEqualTo("参数定义不存在");
        verify(mapper, never()).updateById(any(PipelineJobParamEntity.class));
    }

    @Test
    @DisplayName("PIPE-032: 删除不存在参数返回 10003")
    void deleteRejectsMissing() {
        when(mapper.selectById(5L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete(5L));

        assertThat(ex.getMessage()).isEqualTo("参数定义不存在");
    }

    @Test
    @DisplayName("PIPE-032: 详情解析 options 与 headers")
    void getReturnsParsedOptionsAndHeaders() {
        PipelineJobParamEntity entity = entity("PROFILE", "select", "dev");
        entity.setOptionsJson("[\"dev\",\"prod\"]");
        entity.setApiHeadersJson("{\"X-Token\":\"t\"}");
        when(mapper.selectById(5L)).thenReturn(entity);

        JobParamDefinitionVO vo = service.getById(5L);

        assertThat(vo.getOptions()).containsExactly("dev", "prod");
        assertThat(vo.getApiHeaders()).containsEntry("X-Token", "t");
        assertThat(vo.getRequired()).isFalse();
    }

    @Test
    @DisplayName("PIPE-050: 默认参数只返回 runtime 绑定")
    void getDefaultParamsFiltersFixed() {
        PipelineJobParamEntity runtime = entity("PROFILE", "input", "dev");
        runtime.setValueMode("runtime");
        PipelineJobParamEntity fixed = entity("VERSION", "input", "1.0");
        fixed.setValueMode("fixed");
        when(mapper.selectByPipelineId(1L)).thenReturn(List.of(runtime, fixed));

        var params = service.getDefaultParams(1L);

        assertThat(params).hasSize(1);
        assertThat(params.getFirst().getParamKey()).isEqualTo("PROFILE");
    }

    @Test
    @DisplayName("PIPE-050: api_select 默认参数标记 loading")
    void getDefaultParamsMarksApiSelectLoading() {
        PipelineJobParamEntity api = entity("REGION", "api_select", null);
        api.setValueMode("runtime");
        when(mapper.selectByPipelineId(1L)).thenReturn(List.of(api));

        var params = service.getDefaultParams(1L);

        assertThat(params.getFirst().getLoading()).isTrue();
    }

    @Test
    @DisplayName("PIPE-033: 按 job 分组的参数映射")
    void getParamsGroupedByJob() {
        PipelineJobParamEntity first = entity("A", "input", "1");
        first.setJobId(21L);
        PipelineJobParamEntity second = entity("B", "input", "2");
        second.setJobId(21L);
        PipelineJobParamEntity third = entity("C", "input", "3");
        third.setJobId(22L);
        when(mapper.selectByPipelineId(1L)).thenReturn(List.of(first, second, third));

        Map<Long, List<PipelineJobParamEntity>> grouped = service.getParamsByPipelineGroupedByJob(1L);

        assertThat(grouped).containsKeys(21L, 22L);
        assertThat(grouped.get(21L)).hasSize(2);
    }

    @Test
    @DisplayName("PIPE-089: 远程预览支持顶层数组")
    void previewApiSupportsTopLevelArray() throws IOException {
        startServer("/options", 200, "[\"a\",\"b\"]");

        JobParamPreviewResultVO result = service.previewApi(url("/options"), "GET", Map.of(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOptions()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("PIPE-089: 远程预览支持 JSONPath 提取")
    void previewApiSupportsJsonPath() throws IOException {
        startServer("/nested", 200, "{\"data\":[{\"name\":\"a\"},{\"name\":\"b\"}]}");

        JobParamPreviewResultVO result = service.previewApi(url("/nested"), "GET", Map.of(), "$.data[].name");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOptions()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("PIPE-089: 远程预览非 2xx 返回失败")
    void previewApiHandlesNon2xx() throws IOException {
        startServer("/fail", 500, "boom");

        JobParamPreviewResultVO result = service.previewApi(url("/fail"), "GET", Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("500");
    }

    @Test
    @DisplayName("PIPE-089: 非法 URL 返回失败而不是抛异常")
    void previewApiHandlesInvalidUrl() {
        JobParamPreviewResultVO result = service.previewApi("not a url", "GET", Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("API 调用失败");
    }

    private void startServer(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static JobParamSaveDTO dto(String key, String type, String defaultValue) {
        JobParamSaveDTO dto = new JobParamSaveDTO();
        dto.setPipelineId(1L);
        dto.setJobId(21L);
        dto.setParamKey(key);
        dto.setParamLabel(key);
        dto.setParamType(type);
        dto.setDefaultValue(defaultValue);
        return dto;
    }

    private static PipelineJobParamEntity entity(String key, String type, String defaultValue) {
        PipelineJobParamEntity entity = new PipelineJobParamEntity();
        entity.setId(5L);
        entity.setPipelineId(1L);
        entity.setJobId(21L);
        entity.setParamKey(key);
        entity.setParamLabel(key);
        entity.setParamType(type);
        entity.setValueMode("runtime");
        entity.setDefaultValue(defaultValue);
        entity.setRequired(0);
        entity.setSortOrder(0);
        return entity;
    }
}
