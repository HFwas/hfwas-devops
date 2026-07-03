package com.hfwas.devops.user.operlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.user.operlog.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
