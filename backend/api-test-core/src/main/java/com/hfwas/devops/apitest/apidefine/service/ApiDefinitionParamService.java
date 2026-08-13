package com.hfwas.devops.apitest.apidefine.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionParamDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionParamEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionParamMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 接口参数业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionParamService extends ServiceImpl<ApiDefinitionParamMapper, ApiDefinitionParamEntity> {

    /**
     * 批量保存参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSave(Long definitionId, List<ApiDefinitionParamDTO> params, Long userId) {
        for (int i = 0; i < params.size(); i++) {
            ApiDefinitionParamDTO dto = params.get(i);
            ApiDefinitionParamEntity entity = new ApiDefinitionParamEntity();
            entity.setDefinitionId(definitionId);
            entity.setParamType(dto.getParamType());
            entity.setName(dto.getName());
            entity.setDataType(dto.getDataType() != null ? dto.getDataType() : "string");
            entity.setRequired(dto.getRequired() != null && dto.getRequired() ? 1 : 0);
            entity.setDefaultValue(dto.getDefaultValue());
            entity.setDescription(dto.getDescription());
            entity.setParentId(dto.getParentId());
            entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i);
            entity.setExample(dto.getExample());
            save(entity);
        }
    }
}