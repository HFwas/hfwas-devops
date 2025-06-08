package com.hfwas.devops.entity;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/6/6
 */
@Data
public class Node {
    private Long id;
    private Long parentId;
    private String name;
    /**
     * 类型，例如：1、租户，2、部门，3、项目，4、资源
     */
    private String type;
    /**
     * 图片链接地址
     */
    private String icon;
    private String description;
    /**
     * 排序
     */
    private Integer sort;
}
