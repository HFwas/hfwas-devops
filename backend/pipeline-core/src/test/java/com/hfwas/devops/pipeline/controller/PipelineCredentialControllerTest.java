package com.hfwas.devops.pipeline.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.service.PipelineCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-063..076、153、156。
 */
@ExtendWith(MockitoExtension.class)
class PipelineCredentialControllerTest {

    @Mock
    private PipelineCredentialService credentialService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PipelineCredentialController(credentialService))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-063: 凭证列表不含 secret 字段")
    void listReturnsCredentials() throws Exception {
        when(credentialService.list()).thenReturn(List.of(vo(1L, "git")));

        mvc.perform(get("/pipeline/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("git"))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());
    }

    @Test
    @DisplayName("PIPE-071: 凭证详情")
    void getReturnsCredential() throws Exception {
        when(credentialService.get(1L)).thenReturn(vo(1L, "git"));

        mvc.perform(get("/pipeline/credentials/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("PASSWORD"));
    }

    @Test
    @DisplayName("PIPE-076: 分页")
    void pageReturnsPage() throws Exception {
        Page<CredentialVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo(1L, "git")));
        when(credentialService.page(any())).thenReturn(page);

        mvc.perform(post("/pipeline/credentials/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNo\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("git"));
    }

    @Test
    @DisplayName("PIPE-063: 保存返回 id")
    void saveReturnsId() throws Exception {
        when(credentialService.save(any())).thenReturn(5L);

        mvc.perform(post("/pipeline/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"git\",\"kind\":\"PASSWORD\",\"username\":\"ada\",\"secret\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("PIPE-066: 非法类型返回 10002")
    void saveRejectsUnsupportedKind() throws Exception {
        when(credentialService.save(any()))
                .thenThrow(BizException.of(ResultCode.BAD_REQUEST, "凭证类型必须是 PASSWORD、TOKEN 或 KUBECONFIG"));

        mvc.perform(post("/pipeline/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ssh\",\"kind\":\"SSH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("PIPE-074: 删除凭证")
    void deleteReturnsOk() throws Exception {
        mvc.perform(delete("/pipeline/credentials/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(credentialService).delete(1L);
    }

    @Test
    @DisplayName("PIPE-073: 被引用时删除返回 10002")
    void deleteReferencedReturnsBizCode() throws Exception {
        doThrow(BizException.of(ResultCode.BAD_REQUEST, "凭证仍被流水线引用，无法删除"))
                .when(credentialService).delete(1L);

        mvc.perform(delete("/pipeline/credentials/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.msg").value("凭证仍被流水线引用，无法删除"));
    }

    @Test
    @DisplayName("PIPE-153: 跨租户详情返回 10003")
    void getOtherTenantReturnsNotFound() throws Exception {
        when(credentialService.get(1L))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "凭证不存在"));

        mvc.perform(get("/pipeline/credentials/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    private static CredentialVO vo(Long id, String name) {
        CredentialVO vo = new CredentialVO();
        vo.setId(id);
        vo.setName(name);
        vo.setKind("PASSWORD");
        vo.setUsername("ada");
        return vo;
    }
}
