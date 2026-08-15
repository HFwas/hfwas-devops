package com.hfwas.devops.apitest.curl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * cURL 解析请求 DTO
 *
 * @author hfwas
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurlParseDTO {

    /** 原始 cURL 命令字符串 */
    private String curl;
}