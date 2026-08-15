package com.hfwas.devops.apitest.apidefine.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionExtractEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionExtractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 接口变量提取业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionExtractService extends ServiceImpl<ApiDefinitionExtractMapper, ApiDefinitionExtractEntity> {

    /**
     * 根据接口定义ID查询变量提取规则列表
     */
    public List<ApiDefinitionExtractEntity> getByDefinitionId(Long definitionId) {
        return lambdaQuery()
                .eq(ApiDefinitionExtractEntity::getDefinitionId, definitionId)
                .eq(ApiDefinitionExtractEntity::getEnabled, 1)
                .orderByAsc(ApiDefinitionExtractEntity::getSortOrder)
                .list();
    }

    /**
     * 批量保存变量提取规则（先删后插）
     */
    public void batchSave(Long definitionId, List<ApiDefinitionExtractEntity> extracts) {
        lambdaUpdate().eq(ApiDefinitionExtractEntity::getDefinitionId, definitionId).remove();
        if (extracts != null && !extracts.isEmpty()) {
            extracts.forEach(e -> e.setDefinitionId(definitionId));
            saveBatch(extracts);
        }
    }
}