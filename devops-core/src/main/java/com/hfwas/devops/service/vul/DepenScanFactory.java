package com.hfwas.devops.service.vul;

import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author houfei
 * @package com.hfwas.devops.service.vul
 * @date 2025/1/25
 */
public class DepenScanFactory {

    private static Map<String, DevopsDepenScan> strategyMap = new ConcurrentHashMap<>();

    public static DevopsDepenScan getByLanguage(String language) {
        DevopsDepenScan devopsDepenScan = strategyMap.get(language);
        return devopsDepenScan;
    }

    public static void register(String language, DevopsDepenScan devopsDepenScan) {
        Assert.notNull(language,"language can't be null");
        strategyMap.put(language, devopsDepenScan);
    }

}
