package com.hfwas.devops.apitest.apidefine.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionResponseDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionResponseEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 接口响应定义业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionResponseService
        extends ServiceImpl<ApiDefinitionResponseMapper, ApiDefinitionResponseEntity> {

    /**
     * 批量保存响应定义
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSave(Long definitionId, List<ApiDefinitionResponseDTO> responses, Long userId) {
        for (ApiDefinitionResponseDTO dto : responses) {
            ApiDefinitionResponseEntity entity = new ApiDefinitionResponseEntity();
            entity.setDefinitionId(definitionId);
            entity.setStatusCode(dto.getStatusCode() != null ? dto.getStatusCode() : 200);
            entity.setContentType(dto.getContentType() != null ? dto.getContentType() : "application/json");
            entity.setDescription(dto.getDescription());
            entity.setBodySchema(dto.getBodySchema());
            entity.setBodyExample(dto.getBodyExample());
            save(entity);
        }
    }
}