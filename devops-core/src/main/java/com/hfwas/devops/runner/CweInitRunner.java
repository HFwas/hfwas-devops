package com.hfwas.devops.runner;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.google.common.collect.Lists;
import com.hfwas.devops.convert.DevopsVulCweConvert;
import com.hfwas.devops.entity.DevopsVulCwe;
import com.hfwas.devops.mapper.DevopsVulCweMapper;
import com.hfwas.devops.tools.entity.cwe.CweCsv;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author houfei
 * @package com.hfwas.devops.runner
 * @date 2025/1/13
 */
@Component
@Slf4j
public class CweInitRunner implements ApplicationRunner {

    @Resource
    DevopsVulCweMapper devopsVulCweMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<DevopsVulCwe> vulCwes = devopsVulCweMapper.list(null);
        if (CollectionUtils.isNotEmpty(vulCwes)) return;
        Map<Long, DevopsVulCwe> devopsVulCweMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(vulCwes)) {
            devopsVulCweMap = vulCwes.stream().collect(Collectors.toMap(DevopsVulCwe::getId, Function.identity()));
        }

        List<Integer> cweCsvs = Lists.newArrayList(699, 1000, 1194);
        List<CweCsv> cwes = new ArrayList<>();
        List<DevopsVulCwe> devopsVulCwes = new ArrayList<>();
        for (Integer cwe : cweCsvs) {
            InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(String.format("cwe/%s.csv", cwe));
            List<CweCsv> finalCwes = cwes;
            EasyExcel.read(resourceAsStream, CweCsv.class, new PageReadListener<CweCsv>(dataList -> {
                for (CweCsv demoData : dataList) {
                    finalCwes.add(demoData);
                }
            })).sheet().doRead();
            List<DevopsVulCwe> catedevopsVulCwes = finalCwes.stream().sorted(Comparator.comparing(CweCsv::getCweId)).map(cweCsv -> {
                DevopsVulCwe devopsVulCwe = DevopsVulCweConvert.INSTANCE.to(cweCsv);
                devopsVulCwe.setType(cwe);
                return devopsVulCwe;
            }).collect(Collectors.toList());
            devopsVulCwes.addAll(catedevopsVulCwes);
            cwes.clear();
            finalCwes.clear();
        }
        devopsVulCwes = devopsVulCwes.stream().sorted(Comparator.comparing(DevopsVulCwe::getId)).distinct().collect(Collectors.toList());

        List<List<DevopsVulCwe>> partition = Lists.partition(devopsVulCwes, 50);
        for (List<DevopsVulCwe> devopsVulCweList : partition) {
            devopsVulCweMapper.saveBatch(devopsVulCweList);
        }

    }

}
