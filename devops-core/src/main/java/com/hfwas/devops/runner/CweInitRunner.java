package com.hfwas.devops.runner;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.google.common.collect.Lists;
import com.hfwas.devops.tools.entity.cwe.CweCsv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.runner
 * @date 2025/1/13
 */
@Component
@Slf4j
public class CweInitRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> cweCsvs = Lists.newArrayList("699", "1000", "1194");
        List<CweCsv> cwes = new ArrayList<>();
        for (String cwe : cweCsvs) {
            InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(String.format("cwe/%s.csv", cwe));
            EasyExcel.read(resourceAsStream, CweCsv.class, new PageReadListener<CweCsv>(dataList -> {
                for (CweCsv demoData : dataList) {
                    cwes.add(demoData);
                }
            })).sheet().doRead();
        }
        log.info("cwes:{}", cwes);
    }

}
