package com.hfwas.devops.pm.field;

import com.hfwas.devops.pm.field.model.DetailTabDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 详情页 Tab 目录：代码定义可出现的面板；类型 layout 只存启用 id 有序列表。
 */
public final class DetailTabCatalog {

    public static final String DESCRIPTION = "description";
    public static final String ACTIVITY = "activity";
    public static final String COMMENTS = "comments";
    public static final String LINKS = "links";

    private static final List<DetailTabDefinition> ALL = List.of(
            new DetailTabDefinition(DESCRIPTION, "详情", true, true, 1),
            new DetailTabDefinition(ACTIVITY, "操作记录", true, true, 2),
            new DetailTabDefinition(COMMENTS, "评论", true, true, 3),
            new DetailTabDefinition(LINKS, "关联", true, true, 4),
            new DetailTabDefinition("files", "文件", false, false, 10),
            new DetailTabDefinition("branches", "功能分支", false, false, 11),
            new DetailTabDefinition("test_cases", "测试用例", false, false, 12),
            new DetailTabDefinition("tasks", "任务", false, false, 13)
    );

    private static final Map<String, DetailTabDefinition> BY_ID = ALL.stream()
            .collect(Collectors.toMap(DetailTabDefinition::getId, Function.identity(), (a, b) -> a));

    private DetailTabCatalog() {
    }

    public static List<DetailTabDefinition> all() {
        return ALL;
    }

    public static List<DetailTabDefinition> implemented() {
        return ALL.stream().filter(DetailTabDefinition::isImplemented).toList();
    }

    public static boolean isImplemented(String id) {
        DetailTabDefinition def = BY_ID.get(id);
        return def != null && def.isImplemented();
    }

    public static List<String> defaultEnabledIds() {
        return ALL.stream()
                .filter(DetailTabDefinition::isImplemented)
                .filter(DetailTabDefinition::isDefaultEnabled)
                .map(DetailTabDefinition::getId)
                .toList();
    }

    /**
     * 保留已实现 id，去重保序；空则回填默认。
     */
    public static List<String> sanitize(List<String> tabIds) {
        if (tabIds == null || tabIds.isEmpty()) {
            return new ArrayList<>(defaultEnabledIds());
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : tabIds) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            if (isImplemented(id)) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return new ArrayList<>(defaultEnabledIds());
        }
        return new ArrayList<>(unique);
    }

    public static Set<String> implementedIds() {
        return implemented().stream().map(DetailTabDefinition::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
