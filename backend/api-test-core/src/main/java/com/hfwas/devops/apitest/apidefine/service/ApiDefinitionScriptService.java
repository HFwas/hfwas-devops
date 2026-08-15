package com.hfwas.devops.apitest.apidefine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionScriptEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionScriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 接口脚本业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionScriptService extends ServiceImpl<ApiDefinitionScriptMapper, ApiDefinitionScriptEntity> {

    /**
     * 根据接口定义ID查询脚本列表
     */
    public List<ApiDefinitionScriptEntity> getByDefinitionId(Long definitionId) {
        return lambdaQuery()
                .eq(ApiDefinitionScriptEntity::getDefinitionId, definitionId)
                .eq(ApiDefinitionScriptEntity::getEnabled, 1)
                .list();
    }

    /**
     * 根据接口定义ID和类型查询脚本
     */
    public List<ApiDefinitionScriptEntity> getByDefinitionIdAndType(Long definitionId, String scriptType) {
        return lambdaQuery()
                .eq(ApiDefinitionScriptEntity::getDefinitionId, definitionId)
                .eq(ApiDefinitionScriptEntity::getScriptType, scriptType)
                .eq(ApiDefinitionScriptEntity::getEnabled, 1)
                .list();
    }

    /**
     * 批量保存脚本（先删后插）
     */
    public void batchSave(Long definitionId, List<ApiDefinitionScriptEntity> scripts) {
        lambdaUpdate().eq(ApiDefinitionScriptEntity::getDefinitionId, definitionId).remove();
        if (scripts != null && !scripts.isEmpty()) {
            scripts.forEach(s -> s.setDefinitionId(definitionId));
            saveBatch(scripts);
        }
    }
}