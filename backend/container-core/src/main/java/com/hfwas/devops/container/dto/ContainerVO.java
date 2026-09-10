package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class ContainerVO {
    private String name;
    private String image;
    private String state;
    private boolean ready;
    private int restartCount;
    private Integer exitCode;
}