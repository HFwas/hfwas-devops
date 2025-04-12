package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/4/12
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
