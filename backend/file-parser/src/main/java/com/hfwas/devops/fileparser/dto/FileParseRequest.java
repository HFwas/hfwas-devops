package com.hfwas.devops.fileparser.dto;

import lombok.Data;

/**
 * 文件解析请求参数
 */
@Data
public class FileParseRequest {
    /** 解析选项（可选），JSON 格式 */
    private String options;
}