package com.hfwas.devops.limit;

import com.hfwas.devops.entity.DevopsVul;
import com.hfwas.devops.mapper.DevopsVulCpeMapper;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.mapper.DevopsVulSoftwareMapper;
import com.hfwas.devops.thread.NvdDataRunnable;
import com.hfwas.devops.tools.api.vulnerability.NvdApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.limit
 * @date 2025/2/27
 */
@Slf4j
public class NvdApiService {
    private final ApiLimitRate rateLimiter;
    private final NvdApi nvdApi;
    private DevopsVulMapper vulMapper;
    private DevopsVulCpeMapper cpeMapper;
    private DevopsVulSoftwareMapper softwareMapper;
    private static final Integer CORE_SIZE = Runtime.getRuntime().availableProcessors();
    private ExecutorService executorService = Executors.newFixedThreadPool(CORE_SIZE);
    private final String apiKey;

    public NvdApiService(ApiLimitRate rateLimiter, NvdApi nvdApi, DevopsVulMapper vulMapper, DevopsVulCpeMapper cpeMapper, DevopsVulSoftwareMapper softwareMapper, String apiKey) {
        this.rateLimiter = rateLimiter;
        this.nvdApi = nvdApi;
        this.vulMapper = vulMapper;
        this.cpeMapper = cpeMapper;
        this.softwareMapper = softwareMapper;
        this.apiKey = apiKey;
    }

    public void fetchAndSaveData() throws Exception {
        int request = 0;
        List<DevopsVul> vuls = vulMapper.selectList(null);
        for (int index = 1; index < 143; index++) {
            log.info("第{}页, request:{}", index, request++);
            try (ApiLimitRate.Ticket ticket = rateLimiter.getTicket()) {
                NvdDataRunnable nvdDataRunnable = new NvdDataRunnable(vulMapper, cpeMapper, softwareMapper, nvdApi, apiKey, index, vuls);
                executorService.execute(nvdDataRunnable);
            }catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
