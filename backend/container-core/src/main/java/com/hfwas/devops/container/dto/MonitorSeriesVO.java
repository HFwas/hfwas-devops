package com.hfwas.devops.container.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorSeriesVO {
    private Map<String, String> labels;
    private List<MonitorPointVO> points;
}