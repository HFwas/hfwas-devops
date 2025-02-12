package com.hfwas.devops.service.codeScan.pmd;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.google.common.collect.Lists;
import com.hfwas.devops.codeScan.pmd.PmdCsv;
import com.hfwas.devops.service.codeScan.CodeScanApi;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.service.codeScan.pmd
 * @date 2025/2/12
 */
@Slf4j
@Service
public class PmdCodeScan implements CodeScanApi {

    @Override
    public void resolve(MultipartFile multipartFile) {
        String name = multipartFile.getName();
        if (name.endsWith("csv")) {
            throw new RuntimeException("");
        }
        List<@Nullable PmdCsv> pmdCsvs = Lists.newArrayList();
        try {
            EasyExcel.read(multipartFile.getInputStream(), PmdCsv.class, new PageReadListener<PmdCsv>(dataList -> {
                for (PmdCsv demoData : dataList) {
                    pmdCsvs.add(demoData);
                }
            })).sheet().doRead();
        } catch (Exception e) {
            log.info("");
        }
    }

}
