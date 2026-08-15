package com.hfwas.devops.apitest.apidefine.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionAssertionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionAssertionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 接口断言业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionAssertionService extends ServiceImpl<ApiDefinitionAssertionMapper, ApiDefinitionAssertionEntity> {

    /**
     * 根据接口定义ID查询断言列表
     */
    public List<ApiDefinitionAssertionEntity> getByDefinitionId(Long definitionId) {
        return lambdaQuery()
                .eq(ApiDefinitionAssertionEntity::getDefinitionId, definitionId)
                .eq(ApiDefinitionAssertionEntity::getEnabled, 1)
                .orderByAsc(ApiDefinitionAssertionEntity::getSortOrder)
                .list();
    }

    /**
     * 批量保存断言（先删后插）
     */
    public void batchSave(Long definitionId, List<ApiDefinitionAssertionEntity> assertions) {
        lambdaUpdate().eq(ApiDefinitionAssertionEntity::getDefinitionId, definitionId).remove();
        if (assertions != null && !assertions.isEmpty()) {
            assertions.forEach(a -> a.setDefinitionId(definitionId));
            saveBatch(assertions);
        }
    }
}