package com.hfwas.devops.apitest.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.apitest.history.entity.DebugHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调试历史 Mapper
 *
 * @author hfwas
 */
@Mapper
public interface DebugHistoryMapper extends BaseMapper<DebugHistoryEntity> {

}