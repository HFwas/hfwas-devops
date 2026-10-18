package com.hfwas.devops.apitest.collection;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.collection.dto.CollectionCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionItemAddDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionItemBatchDTO;
import com.hfwas.devops.apitest.collection.entity.CollectionItemEntity;
import com.hfwas.devops.apitest.collection.vo.CollectionItemVO;
import com.hfwas.devops.apitest.collection.mapper.CollectionItemMapper;
import com.hfwas.devops.apitest.collection.service.CollectionItemService;
import com.hfwas.devops.apitest.collection.service.CollectionService;
import com.hfwas.devops.apitest.collection.vo.CollectionVO;
import com.hfwas.devops.apitest.support.TestProjectAccessValidator;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CollectionItemService — 集合项项目隔离测试。
 *
 * @author hfwas
 */
@DisplayName("CollectionItemService — 集合项项目隔离")
class CollectionItemServiceTest extends BaseApiTest {

    @Autowired
    private CollectionItemService itemService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ApiDefinitionMapper definitionMapper;

    @Autowired
    private CollectionItemMapper itemMapper;

    @Autowired
    private TestProjectAccessValidator projectAccessValidator;

    @AfterEach
    void resetAccessContext() {
        projectAccessValidator.reset();
    }

    @Test
    @DisplayName("add 引用其他项目的 definition 应返回 20002")
    void definitionMustMatchCollectionProject() {
        Long collectionId = createCollection(testProjectId());
        ApiDefinitionEntity foreignDefinition = insertDefinition(2002L, "/api/foreign");
        projectAccessValidator.allow(testProjectId());

        CollectionItemAddDTO dto = new CollectionItemAddDTO();
        dto.setDefinitionId(foreignDefinition.getId());

        BizException ex = assertThrows(BizException.class, () -> itemService.add(collectionId, dto));
        assertEquals(ResultCode.PROJECT_ACCESS_DENIED.getCode(), ex.getCode());
        assertEquals(0L, countItems(collectionId));
    }

    @Test
    @DisplayName("batchAdd 跳过其他项目的 definition，不写入")
    void batchAddSkipsForeignDefinition() {
        Long collectionId = createCollection(testProjectId());
        ApiDefinitionEntity ownDefinition = insertDefinition(testProjectId(), "/api/own");
        ApiDefinitionEntity foreignDefinition = insertDefinition(2002L, "/api/foreign");
        projectAccessValidator.allow(testProjectId());

        CollectionItemBatchDTO dto = new CollectionItemBatchDTO();
        dto.setDefinitionIds(List.of(ownDefinition.getId(), foreignDefinition.getId()));

        itemService.batchAdd(collectionId, dto);

        assertEquals(1L, countItems(collectionId));
        CollectionItemEntity saved = itemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CollectionItemEntity>()
                        .eq(CollectionItemEntity::getCollectionId, collectionId));
        assertEquals(ownDefinition.getId(), saved.getDefinitionId());
    }

    @Test
    @DisplayName("跨项目集合的 add/update/delete/reorder 应全部 20002")
    void crossProjectCollectionForbidden() {
        Long foreignCollectionId = createCollection(2002L);
        ApiDefinitionEntity definition = insertDefinition(2002L, "/api/foreign");
        CollectionItemAddDTO dto = new CollectionItemAddDTO();
        dto.setDefinitionId(definition.getId());
        CollectionItemVO foreignItem = itemService.add(foreignCollectionId, dto);

        projectAccessValidator.allow(testProjectId());

        assertProjectDenied(() -> itemService.add(foreignCollectionId, dto));
        assertProjectDenied(() -> itemService.batchAdd(foreignCollectionId, new CollectionItemBatchDTO()));
        assertProjectDenied(() -> itemService.update(foreignCollectionId, foreignItem.getId(), dto));
        assertProjectDenied(() -> itemService.delete(foreignCollectionId, foreignItem.getId()));
        assertProjectDenied(() -> itemService.reorder(foreignCollectionId, List.of(foreignItem.getId())));
        assertEquals(1L, countItems(foreignCollectionId));
    }

    private Long createCollection(Long projectId) {
        CollectionCreateDTO dto = new CollectionCreateDTO();
        dto.setName("集合-" + projectId);
        CollectionVO vo = collectionService.create(dto, projectId);
        return vo.getId();
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

    private long countItems(Long collectionId) {
        return itemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CollectionItemEntity>()
                        .eq(CollectionItemEntity::getCollectionId, collectionId));
    }

    private void assertProjectDenied(org.junit.jupiter.api.function.Executable executable) {
        BizException ex = assertThrows(BizException.class, executable);
        assertEquals(ResultCode.PROJECT_ACCESS_DENIED.getCode(), ex.getCode());
    }
}
