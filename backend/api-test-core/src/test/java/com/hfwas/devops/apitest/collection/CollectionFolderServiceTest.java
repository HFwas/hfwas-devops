package com.hfwas.devops.apitest.collection;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.collection.dto.CollectionCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionFolderCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionFolderUpdateDTO;
import com.hfwas.devops.apitest.collection.service.CollectionFolderService;
import com.hfwas.devops.apitest.collection.service.CollectionService;
import com.hfwas.devops.apitest.collection.vo.CollectionFolderVO;
import com.hfwas.devops.apitest.collection.vo.CollectionVO;
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
 * CollectionFolderService — 文件夹项目隔离测试。
 *
 * @author hfwas
 */
@DisplayName("CollectionFolderService — 文件夹项目隔离")
class CollectionFolderServiceTest extends BaseApiTest {

    @Autowired
    private CollectionFolderService folderService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private TestProjectAccessValidator projectAccessValidator;

    @AfterEach
    void resetAccessContext() {
        projectAccessValidator.reset();
    }

    @Test
    @DisplayName("跨项目 create/update/delete/tree 应全部 20002")
    void crossProjectForbidden() {
        Long collectionId = createCollection(2002L);
        CollectionFolderVO folder = folderService.create(collectionId, buildCreateDTO("外部文件夹"));

        projectAccessValidator.allow(testProjectId());

        assertProjectDenied(() -> folderService.create(collectionId, buildCreateDTO("另一个文件夹")));
        assertProjectDenied(() -> folderService.update(collectionId, folder.getId(), new CollectionFolderUpdateDTO()));
        assertProjectDenied(() -> folderService.delete(collectionId, folder.getId()));
        assertProjectDenied(() -> folderService.getTree(collectionId));
    }

    private Long createCollection(Long projectId) {
        CollectionCreateDTO dto = new CollectionCreateDTO();
        dto.setName("集合-" + projectId);
        CollectionVO vo = collectionService.create(dto, projectId);
        return vo.getId();
    }

    private CollectionFolderCreateDTO buildCreateDTO(String name) {
        CollectionFolderCreateDTO dto = new CollectionFolderCreateDTO();
        dto.setName(name);
        return dto;
    }

    private void assertProjectDenied(org.junit.jupiter.api.function.Executable executable) {
        BizException ex = assertThrows(BizException.class, executable);
        assertEquals(ResultCode.PROJECT_ACCESS_DENIED.getCode(), ex.getCode());
    }
}
