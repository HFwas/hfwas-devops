package com.hfwas.devops.apitest.apidefine.convert;

import com.hfwas.devops.apitest.apidefine.dto.ApiGroupCreateDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiGroupEntity;
import com.hfwas.devops.apitest.apidefine.vo.ApiGroupVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 分组对象转换
 *
 * @author hfwas
 */
@Mapper(componentModel = "spring")
public interface ApiGroupConvert {

    ApiGroupEntity toEntity(ApiGroupCreateDTO dto);

    @Mapping(target = "createdBy", source = "createBy")
    ApiGroupVO toVO(ApiGroupEntity entity);

    @Mapping(target = "children", ignore = true)
    @Mapping(target = "apiCount", ignore = true)
    @Mapping(target = "createdBy", source = "createBy")
    ApiGroupVO toTreeVO(ApiGroupEntity entity);
}