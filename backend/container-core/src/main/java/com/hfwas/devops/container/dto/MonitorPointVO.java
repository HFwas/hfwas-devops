package com.hfwas.devops.container.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorPointVO {
    private long timestamp;
    private double value;
}