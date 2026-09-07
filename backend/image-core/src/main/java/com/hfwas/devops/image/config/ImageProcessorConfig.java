package com.hfwas.devops.image.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "image-processor")
public class ImageProcessorConfig {

    /** 单文件大小上限 */
    private DataSize maxFileSize = DataSize.ofMegabytes(50);

    /** 会话过期时间 */
    private Duration sessionTtl = Duration.ofMinutes(30);

    /** 临时目录 */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/hfwas-image";

    /** 宽×高上限，防 decompression bomb */
    private long maxPixels = 40_000_000L;

    /** 超过该像素数的 convert 改为异步 job */
    private long asyncMinPixels = 16_000_000L;

    private Preview preview = new Preview();

    private Engines engines = new Engines();

    @Data
    public static class Preview {
        private int maxSide = 2048;
        private int quality = 85;
    }

    @Data
    public static class Engines {
        private boolean magickEnabled = true;
        private String magickPath = "magick";
        private boolean exiftoolEnabled = true;
        private String exiftoolPath = "exiftool";
        private Duration processTimeout = Duration.ofSeconds(30);
        private Duration convertTimeout = Duration.ofSeconds(60);
        private Duration previewTimeout = Duration.ofSeconds(15);
        private int maxConcurrent = 2;
    }

    public long maxFileSizeBytes() {
        return maxFileSize.toBytes();
    }
}
