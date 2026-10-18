package com.hfwas.devops.apitest.history;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.history.dto.DebugHistoryQueryDTO;
import com.hfwas.devops.apitest.history.entity.DebugHistoryEntity;
import com.hfwas.devops.apitest.history.mapper.DebugHistoryMapper;
import com.hfwas.devops.apitest.history.service.DebugHistoryService;
import com.hfwas.devops.apitest.support.TestProjectAccessValidator;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DebugHistoryService — 调试历史项目隔离测试。
 *
 * @author hfwas
 */
@DisplayName("DebugHistoryService — 调试历史项目隔离")
class DebugHistoryServiceTest extends BaseApiTest {

    @Autowired
    private DebugHistoryService historyService;

    @Autowired
    private DebugHistoryMapper historyMapper;

    @Autowired
    private ApiDefinitionMapper definitionMapper;

    @Autowired
    private TestProjectAccessValidator projectAccessValidator;

    @AfterEach
    void resetAccessContext() {
        projectAccessValidator.reset();
    }

    @Test
    @DisplayName("跨项目 detail/by-definition/delete/deleteBatch 应全部 20002")
    void crossProjectForbidden() {
        Long ownId = insertHistory(1001L, "/api/own");
        Long foreignId = insertHistory(2002L, "/api/foreign");
        ApiDefinitionEntity foreignDefinition = insertDefinition(2002L, "/api/foreign");

        projectAccessValidator.allow(testProjectId());

        assertProjectDenied(() -> historyService.getDetail(foreignId));
        assertProjectDenied(() -> historyService.delete(foreignId));
        assertProjectDenied(() -> historyService.getByDefinitionId(foreignDefinition.getId(), 20));
        assertProjectDenied(() -> historyService.deleteBatch(List.of(ownId, foreignId)));

        // 批量删除被整体拒绝，自己的记录仍在
        assertNotNull(historyMapper.selectById(ownId));
        assertNotNull(historyMapper.selectById(foreignId));
    }

    @Test
    @DisplayName("keyword 命中他人 requestUrl 时不得绕过项目过滤")
    void keywordDoesNotBypassProject() {
        Long ownId = insertHistory(1001L, "/api/own");
        insertHistory(2002L, "https://foreign.example.com/secret");

        projectAccessValidator.allow(testProjectId());

        DebugHistoryQueryDTO keywordQuery = new DebugHistoryQueryDTO();
        keywordQuery.setKeyword("secret");
        assertEquals(0, historyService.pageQuery(keywordQuery).getTotal());

        DebugHistoryQueryDTO ownQuery = new DebugHistoryQueryDTO();
        ownQuery.setKeyword("own");
        var page = historyService.pageQuery(ownQuery);
        assertEquals(1, page.getTotal());
        assertEquals(ownId, page.getRecords().get(0).getId());
    }

    @Test
    @DisplayName("显式 projectId 跨项目应 20002")
    void explicitProjectFilterGuarded() {
        DebugHistoryQueryDTO query = new DebugHistoryQueryDTO();
        query.setProjectId(2002L);

        projectAccessValidator.allow(testProjectId());

        assertProjectDenied(() -> historyService.pageQuery(query));
    }

    @Test
    @DisplayName("不带 projectId 时只返回可见项目")
    void pageScopedToAccessibleProjects() {
        Long ownId = insertHistory(1001L, "/api/own");
        insertHistory(2002L, "/api/foreign");

        projectAccessValidator.allow(testProjectId());
        var page = historyService.pageQuery(new DebugHistoryQueryDTO());
        assertEquals(1, page.getTotal());
        assertEquals(ownId, page.getRecords().get(0).getId());

        projectAccessValidator.denyAll();
        assertTrue(historyService.pageQuery(new DebugHistoryQueryDTO()).getRecords().isEmpty());
    }

    private Long insertHistory(Long projectId, String url) {
        DebugHistoryEntity entity = new DebugHistoryEntity();
        entity.setProjectId(projectId);
        entity.setName("调试-" + url);
        entity.setRequestUrl(url);
        entity.setRequestMethod("GET");
        entity.setStatus("SUCCESS");
        historyMapper.insert(entity);
        return entity.getId();
    }

    private ApiDefinitionEntity insertDefinition(Long projectId, String path) {
        ApiDefinitionEntity definition = new ApiDefinitionEntity();
        definition.setProjectId(projectId);
        definition.setName("接口-" + path);
        definition.setPath(path);
        definition.setMethod("GET");
        definition.setStatus("DRAFT");
        definitionMapper.insert(definition);
        return definition;
    }

    private void assertProjectDenied(org.junit.jupiter.api.function.Executable executable) {
        BizException ex = assertThrows(BizException.class, executable);
        assertEquals(ResultCode.PROJECT_ACCESS_DENIED.getCode(), ex.getCode());
    }
}
