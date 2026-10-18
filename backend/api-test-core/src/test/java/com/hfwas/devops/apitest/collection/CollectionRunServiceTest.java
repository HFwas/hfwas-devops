package com.hfwas.devops.apitest.collection;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.collection.dto.CollectionCreateDTO;
import com.hfwas.devops.apitest.collection.entity.CollectionRunEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionRunMapper;
import com.hfwas.devops.apitest.collection.service.CollectionRunService;
import com.hfwas.devops.apitest.collection.service.CollectionService;
import com.hfwas.devops.apitest.collection.vo.CollectionVO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentCreateDTO;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import com.hfwas.devops.apitest.environment.vo.EnvironmentDetailVO;
import com.hfwas.devops.apitest.support.TestProjectAccessValidator;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CollectionRunService — 集合运行项目隔离测试。
 *
 * @author hfwas
 */
@DisplayName("CollectionRunService — 集合运行项目隔离")
class CollectionRunServiceTest extends BaseApiTest {

    @Autowired
    private CollectionRunService runService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private CollectionRunMapper runMapper;

    @Autowired
    private TestProjectAccessValidator projectAccessValidator;

    @AfterEach
    void resetAccessContext() {
        projectAccessValidator.reset();
    }

    @Test
    @DisplayName("execute 使用其他项目环境应返回 20002")
    void environmentMustMatchCollectionProject() {
        Long collectionId = createCollection(testProjectId());
        Long foreignEnvironmentId = createEnvironment(2002L);

        projectAccessValidator.allow(testProjectId());

        BizException ex = assertThrows(BizException.class,
                () -> runService.execute(collectionId, foreignEnvironmentId));
        assertEquals(ResultCode.PROJECT_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("runId 详情/删除按集合项目校验，跨项目应 20002")
    void runIdScopedByCollectionProject() {
        Long foreignCollectionId = createCollection(2002L);
        Long runId = insertRun(foreignCollectionId, 2002L);

        projectAccessValidator.allow(testProjectId());

        assertProjectDenied(() -> runService.getDetail(runId));
        assertProjectDenied(() -> runService.delete(runId));
        assertProjectDenied(() -> runService.pageQuery(foreignCollectionId, 1, 20));
    }

    private Long createCollection(Long projectId) {
        CollectionCreateDTO dto = new CollectionCreateDTO();
        dto.setName("集合-" + projectId);
        CollectionVO vo = collectionService.create(dto, projectId);
        return vo.getId();
    }

    private Long createEnvironment(Long projectId) {
        EnvironmentCreateDTO dto = new EnvironmentCreateDTO();
        dto.setName("环境-" + projectId);
        EnvironmentDetailVO vo = environmentService.create(dto, projectId);
        return vo.getId();
    }

    private Long insertRun(Long collectionId, Long projectId) {
        CollectionRunEntity run = new CollectionRunEntity();
        run.setCollectionId(collectionId);
        run.setProjectId(projectId);
        run.setName("Run - test");
        run.setStatus("COMPLETED");
        run.setTotalCount(0);
        run.setPassedCount(0);
        run.setFailedCount(0);
        run.setErrorCount(0);
        run.setDurationMs(0L);
        run.setTriggerMode("MANUAL");
        runMapper.insert(run);
        return run.getId();
    }

    private void assertProjectDenied(org.junit.jupiter.api.function.Executable executable) {
        BizException ex = assertThrows(BizException.class, executable);
        assertEquals(ResultCode.PROJECT_ACCESS_DENIED.getCode(), ex.getCode());
    }
}
