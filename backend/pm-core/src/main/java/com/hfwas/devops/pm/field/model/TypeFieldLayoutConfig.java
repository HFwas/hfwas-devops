package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TypeFieldLayoutConfig {
    private List<String> listFields = new ArrayList<>();
    private List<String> searchFields = new ArrayList<>();
    private List<String> createFields = new ArrayList<>();
    /** 详情页 Tab id 有序列表；空则使用目录默认 */
    private List<String> detailTabs = new ArrayList<>();
    /** 事项类型功能（如导入导出）；缺省由 FeatureCatalog 回填 */
    private TypeFeaturesConfig features;
}
