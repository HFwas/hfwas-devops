package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

/**
 * 集合运行项结果 VO
 *
 * @author hfwas
 */
@Data
public class CollectionRunItemVO {

    private Long id;
    private Long runId;
    private Long collectionItemId;
    private Long definitionId;
    private String name;
    private String requestUrl;
    private String requestMethod;
    private String requestHeaders;
    private String requestBody;
    private Integer responseStatusCode;
    private String responseHeaders;
    private String responseBody;
    private Long responseSize;
    private Long durationMs;
    private String status;
    private String errorMessage;
    private String assertionResults;
    private Boolean allAssertionsPassed;
    private String extractedVariables;
    private Integer sortOrder;
}