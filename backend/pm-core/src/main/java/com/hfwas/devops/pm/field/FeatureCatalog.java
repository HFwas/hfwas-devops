package com.hfwas.devops.pm.field;

import com.hfwas.devops.pm.field.model.FeatureDefinition;
import com.hfwas.devops.pm.field.model.TypeFeaturesConfig;
import com.hfwas.devops.pm.field.model.WorkItemIoFeatureConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 事项类型功能目录：代码定义可挂载能力；类型 layout 存启用与配置。
 */
public final class FeatureCatalog {

    public static final String WORK_ITEM_IO = "work_item_io";
    public static final String SURFACE_LIST_ACTIONS = "list_actions";

    private static final List<FeatureDefinition> ALL = List.of(
            new FeatureDefinition(
                    WORK_ITEM_IO,
                    "导入导出",
                    true,
                    true,
                    1,
                    List.of(SURFACE_LIST_ACTIONS))
    );

    private static final Map<String, FeatureDefinition> BY_ID = ALL.stream()
            .collect(Collectors.toMap(FeatureDefinition::getId, Function.identity(), (a, b) -> a));

    private FeatureCatalog() {
    }

    public static List<FeatureDefinition> all() {
        return ALL;
    }

    public static List<FeatureDefinition> implemented() {
        return ALL.stream().filter(FeatureDefinition::isImplemented).toList();
    }

    public static boolean isImplemented(String id) {
        FeatureDefinition def = BY_ID.get(id);
        return def != null && def.isImplemented();
    }

    public static TypeFeaturesConfig defaultFeatures() {
        TypeFeaturesConfig features = new TypeFeaturesConfig();
        features.setWorkItemIo(defaultWorkItemIo());
        return features;
    }

    public static WorkItemIoFeatureConfig defaultWorkItemIo() {
        WorkItemIoFeatureConfig io = new WorkItemIoFeatureConfig();
        io.setEnabled(true);
        io.setExportFieldKeys(new ArrayList<>());
        io.setImportFieldKeys(new ArrayList<>());
        return io;
    }

    /**
     * 缺省补齐 work_item_io；enabled 缺省为 true；字段 key 去重保序，非法 key 可按 allowedKeys 过滤。
     */
    public static TypeFeaturesConfig sanitize(TypeFeaturesConfig source) {
        return sanitize(source, null);
    }

    public static TypeFeaturesConfig sanitize(TypeFeaturesConfig source, Set<String> allowedFieldKeys) {
        TypeFeaturesConfig result = new TypeFeaturesConfig();
        WorkItemIoFeatureConfig raw = source != null ? source.getWorkItemIo() : null;
        result.setWorkItemIo(sanitizeWorkItemIo(raw, allowedFieldKeys));
        return result;
    }

    public static WorkItemIoFeatureConfig sanitizeWorkItemIo(WorkItemIoFeatureConfig source,
                                                            Set<String> allowedFieldKeys) {
        WorkItemIoFeatureConfig io = new WorkItemIoFeatureConfig();
        if (source == null) {
            io.setEnabled(true);
            io.setExportFieldKeys(new ArrayList<>());
            io.setImportFieldKeys(new ArrayList<>());
            return io;
        }
        io.setEnabled(source.getEnabled() == null || Boolean.TRUE.equals(source.getEnabled()));
        io.setExportFieldKeys(sanitizeFieldKeys(source.getExportFieldKeys(), allowedFieldKeys));
        io.setImportFieldKeys(sanitizeFieldKeys(source.getImportFieldKeys(), allowedFieldKeys));
        return io;
    }

    private static List<String> sanitizeFieldKeys(List<String> keys, Set<String> allowedFieldKeys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : keys) {
            if (raw == null) {
                continue;
            }
            String key = raw.trim();
            if (key.isEmpty()) {
                continue;
            }
            if (allowedFieldKeys != null && !allowedFieldKeys.contains(key)) {
                continue;
            }
            unique.add(key);
        }
        return new ArrayList<>(unique);
    }
}
