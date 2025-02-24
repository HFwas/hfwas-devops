package com.hfwas.devops.service.sync.nvd;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hfwas.devops.mapper.DevopsVulMapper;
import com.hfwas.devops.tools.entity.nvd.file.CveItem;
import com.hfwas.devops.tools.entity.nvd.file.NvdCveFeed;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

/**
 * @author houfei
 * @package com.hfwas.devops.service.sync.nvd
 * @date 2025/2/22
 */
@Slf4j
public class NvdApiDataCallable implements Callable<NvdApiDataCallable> {

    private DevopsVulMapper vulMapper;
    private ObjectMapper objectMapper = new ObjectMapper();
    private JsonParser jsonParser;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private File nvdFile;
    private CveItem nextItem;

    public NvdApiDataCallable(DevopsVulMapper vulMapper, File nvdFile) {
        this.startTime = LocalDateTime.now();
        this.vulMapper = vulMapper;
        this.nvdFile = nvdFile;
        objectMapper.registerModule(new JavaTimeModule());
        // 配置 ObjectMapper 以确保所有字段都被序列化
        objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true);
    }

    @Override
    public NvdApiDataCallable call() throws Exception {
        try (InputStream fis = Files.newInputStream(nvdFile.toPath());
             InputStream inputStream = new BufferedInputStream(new GZIPInputStream(fis))) {
            NvdCveFeed nvdCveFeed = objectMapper.readValue(inputStream, NvdCveFeed.class);
            log.info("",nvdCveFeed);
        }
        this.endTime = LocalDateTime.now();
        return null;
    }
}
