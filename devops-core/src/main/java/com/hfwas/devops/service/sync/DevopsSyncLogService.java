package com.hfwas.devops.service.sync;

import com.hfwas.devops.mapper.DevopsSyncLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author houfei
 * @package com.hfwas.devops.service.sync
 * @date 2025/2/13
 */
@Service
public class DevopsSyncLogService {

    @Resource
    private DevopsSyncLogMapper syncLogMapper;



}
