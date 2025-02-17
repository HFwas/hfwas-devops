package com.hfwas.devops.service.metrics;

import org.springframework.stereotype.Service;

/**
 * @author houfei
 * @package com.hfwas.devops.service.metrics
 * @date 2025/2/17
 */
@Service
public class MetricsServiceImpl implements MetricsService{

    @Override
    public ProcessHandle processHandle() {
        ProcessHandle current = ProcessHandle.current();
        return current;
    }

}
