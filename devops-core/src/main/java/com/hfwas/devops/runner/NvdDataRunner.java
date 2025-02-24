package com.hfwas.devops.runner;

import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.service.sync.nvd.NvdApiDataCallable;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * @author houfei
 * @package com.hfwas.devops.runner
 * @date 2025/2/22
 */
@Component
@Slf4j
public class NvdDataRunner implements ApplicationRunner {

    private static final Integer THREAD_NUM = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(THREAD_NUM);

    @Resource
    private DevopsVulMapper vulMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 获取 resources/nvd 目录的 URL
        URL resource = NvdDataRunner.class.getClassLoader().getResource("nvd");
        // 将 URL 转换为 Path 对象
        Path nvdPath = Paths.get(resource.toURI());
        Stream<Path> walk = Files.walk(nvdPath);
        walk.forEach(path -> {
            File file = new File(path.toString());
            if (file.exists() && file.getName().endsWith("json.gz")) {
                EXECUTOR_SERVICE.submit(new NvdApiDataCallable(vulMapper, file));
            }
        });
    }

}
