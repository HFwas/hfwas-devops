package com.hfwas.devops.limit;

import com.hfwas.devops.mapper.DevopsVulCpeMapper;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.mapper.DevopsVulSoftwareMapper;
import com.hfwas.devops.thread.NvdDataRunnable;
import com.hfwas.devops.tools.api.vulnerability.NvdApi;
import com.hfwas.devops.tools.entity.nvd.NvdResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

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

    @Value("${devops.vulnerability.nvd.api-key}")
    private String apiKey;

    public NvdApiService(ApiLimitRate rateLimiter, NvdApi nvdApi, DevopsVulMapper vulMapper, DevopsVulCpeMapper cpeMapper, DevopsVulSoftwareMapper softwareMapper) {
        this.rateLimiter = rateLimiter;
        this.nvdApi = nvdApi;
        this.vulMapper = vulMapper;
        this.cpeMapper = cpeMapper;
        this.softwareMapper = softwareMapper;
    }

    public void fetchAndSaveData() throws Exception {
        for (int i = 1; i < 143; i++) {
            log.info("第{}页", i);
            try (ApiLimitRate.Ticket ticket = rateLimiter.getTicket()) {
                NvdResponse cves = nvdApi.nvd(apiKey, i, 2000);
                NvdDataRunnable nvdDataRunnable = new NvdDataRunnable(cves, vulMapper, cpeMapper, softwareMapper);
                executorService.execute(nvdDataRunnable);
            }catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
