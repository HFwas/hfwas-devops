package com.hfwas.devops.pm.workitem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PmWorkItemMapper extends BaseMapper<PmWorkItem> {

    @Select("SELECT type_code AS typeCode, COUNT(*) AS cnt FROM pm_work_item " +
            "WHERE project_id = #{projectId} AND del_flag = 0 GROUP BY type_code")
    List<Map<String, Object>> countByType(@Param("projectId") Long projectId);
}
