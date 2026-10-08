package com.hfwas.devops.container.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("helm_chart_artifact")
public class HelmChartArtifactEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repositoryId;
    private Long tenantId;
    private String chartName;
    private String version;
    private String digest;
    private Long sizeBytes;
    private String chartRef;
    private String description;
    private String appVersion;
    /** JSON array of Chart.yaml keywords. */
    private String keywords;
    private String readme;
    private String valuesYaml;
    /** True once README and values were extracted from the package or a pull. */
    private Boolean contentCached;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
