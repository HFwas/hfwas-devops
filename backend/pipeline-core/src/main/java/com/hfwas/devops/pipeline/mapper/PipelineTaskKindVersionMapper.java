package com.hfwas.devops.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PipelineTaskKindVersionMapper extends BaseMapper<PipelineTaskKindVersionEntity> {

    @Select("""
            SELECT * FROM pipeline_task_kind_version
            WHERE kind_value = #{kindValue}
            ORDER BY create_time DESC, id DESC
            """)
    List<PipelineTaskKindVersionEntity> selectByKind(String kindValue);

    @Select("""
            SELECT * FROM pipeline_task_kind_version
            WHERE kind_value = #{kindValue} AND version = #{version}
            LIMIT 1
            """)
    PipelineTaskKindVersionEntity selectByKindAndVersion(
            @Param("kindValue") String kindValue,
            @Param("version") String version
    );
}
