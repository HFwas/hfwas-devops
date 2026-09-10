package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class TagVO {
    private String name;
    private String pushTime;
    private String pullTime;
    private Boolean immutable;
}