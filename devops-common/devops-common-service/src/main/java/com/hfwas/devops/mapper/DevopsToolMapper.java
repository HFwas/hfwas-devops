package com.hfwas.devops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hfwas.devops.entity.DevopsTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.mapper
 * @date 2025/3/16
 */
@Mapper
public interface DevopsToolMapper extends BaseMapper<DevopsTool> {

    List<DevopsTool> listTool(@Param("devopsTool") DevopsTool devopsTool);

    Boolean saveDevopsTool(@Param("devopsTool") DevopsTool devopsTool);

    Boolean updateDevopsToolById(@Param("devopsTool") DevopsTool devopsTool);

    Boolean removeDevopsToolById(@Param("id") Integer id);

}
