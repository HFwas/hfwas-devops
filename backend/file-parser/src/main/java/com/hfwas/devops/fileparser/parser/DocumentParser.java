package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;

import java.io.File;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 解析文件
     * @param file 待解析文件
     * @param fileName 原始文件名
     * @return 解析结果
     */
    FileParseResultVO parse(File file, String fileName);

    /**
     * 是否支持该 MIME 类型
     */
    boolean supports(String mimeType);
}