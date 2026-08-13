package com.hfwas.devops.apitest.apidefine.convert;

import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionUpdateDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionDetailVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 接口定义对象转换
 *
 * @author hfwas
 */
@Mapper(componentModel = "spring")
public interface ApiDefinitionConvert {

    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "version", constant = "1.0.0")
    ApiDefinitionEntity toEntity(ApiDefinitionCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    void updateEntity(ApiDefinitionUpdateDTO dto, @MappingTarget ApiDefinitionEntity entity);

    @Mapping(target = "groupName", ignore = true)
    ApiDefinitionVO toVO(ApiDefinitionEntity entity);

    @Mapping(target = "groupName", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "responses", ignore = true)
    ApiDefinitionDetailVO toDetailVO(ApiDefinitionEntity entity);
}