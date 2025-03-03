package com.hfwas.devops.service.sync;

import com.hfwas.devops.limit.ApiLimitRate;
import com.hfwas.devops.limit.NvdApiService;
import com.hfwas.devops.mapper.DevopsVulCpeMapper;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.mapper.DevopsVulSoftwareMapper;
import com.hfwas.devops.tools.api.vulnerability.NvdApi;
import com.hfwas.devops.tools.api.vulnerability.NvdFeedsApi;
import feign.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author houfei
 * @package com.hfwas.devops.service.sync
 * @date 2025/2/21
 */
@Service("NvdVulSyncApiImpl")
@Slf4j
public class NvdVulSyncApiImpl implements VulSyncApi {

    private static final Integer START_YEAR = 2002;
    private static final Integer END_YEAR = LocalDate.now().getYear();
    private static final String META_FILE_NAME = "nvdcve-1.1-%s.meta";
    private static final Integer CORE_SIZE = Runtime.getRuntime().availableProcessors();
    @Resource
    private NvdApi nvdApi;
    @Resource
    private NvdFeedsApi nvdFeedsApi;
    @Resource
    private DevopsVulMapper vulMapper;
    @Resource
    private DevopsVulCpeMapper cpeMapper;
    @Resource
    private DevopsVulSoftwareMapper softwareMapper;

    @Override
    public void sync() {
        Map<String, Properties> updateYearMaps = new HashMap<>();
        try {
            ApiLimitRate rateLimiter = new ApiLimitRate(50, 30 * 1000); // 5 requests per second
            NvdApiService nvdApiService = new NvdApiService(rateLimiter, nvdApi, vulMapper, cpeMapper, softwareMapper);
            nvdApiService.fetchAndSaveData();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> updateYearMap() {
        return null;
    }

    private Map<String, Properties> fetchRemoteMeta() throws IOException {
        HashMap<String, Properties> propertiesHashMap = new HashMap<>();
        for (int i = START_YEAR; i <= END_YEAR; i++) {
            final Properties properties = new Properties();
            String name = String.format(META_FILE_NAME, i);
            // https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-2002.meta
            Response nvdMeta = nvdFeedsApi.meta(name);
            Response.Body body = nvdMeta.body();
            byte[] bytes = body.asInputStream().readAllBytes();
            String content = new String(bytes, Charset.defaultCharset());
            properties.load(new StringReader(content));
            propertiesHashMap.put(name, properties);
        }
        return propertiesHashMap;
    }

}
