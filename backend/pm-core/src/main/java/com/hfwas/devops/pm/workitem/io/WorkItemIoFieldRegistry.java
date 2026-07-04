package com.hfwas.devops.pm.workitem.io;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkItemIoFieldRegistry {

    public static final String ITEM_KEY = "itemKey";

    private final FieldDefinitionService fieldDefinitionService;

    public List<WorkItemIoColumn> listColumns(Long projectId, String typeCode) {
        List<FieldDefinition> defs = fieldDefinitionService.listByProjectAndType(projectId, typeCode);
        List<WorkItemIoColumn> columns = new ArrayList<>();
        columns.add(itemKeyColumn());
        for (FieldDefinition def : defs) {
            if ("type_code".equals(def.getFieldKey())) {
                continue;
            }
            WorkItemIoColumn col = new WorkItemIoColumn();
            col.setFieldKey(def.getFieldKey());
            col.setFieldName(def.getFieldName());
            col.setFieldType(def.getFieldType());
            col.setSystemField(def.getSystemFlag() != null && def.getSystemFlag() == 1);
            col.setDefaultSelected(Boolean.TRUE.equals(def.getShowInList()) || isCoreField(def.getFieldKey()));
            columns.add(col);
        }
        return columns;
    }

    public Map<String, WorkItemIoColumn> columnMap(Long projectId, String typeCode) {
        Map<String, WorkItemIoColumn> map = new LinkedHashMap<>();
        for (WorkItemIoColumn col : listColumns(projectId, typeCode)) {
            map.put(col.getFieldKey(), col);
        }
        return map;
    }

    public Map<String, FieldDefinition> fieldDefinitionMap(Long projectId, String typeCode) {
        Map<String, FieldDefinition> map = new LinkedHashMap<>();
        for (FieldDefinition def : fieldDefinitionService.listByProjectAndType(projectId, typeCode)) {
            map.put(def.getFieldKey(), def);
        }
        return map;
    }

    private WorkItemIoColumn itemKeyColumn() {
        WorkItemIoColumn col = new WorkItemIoColumn();
        col.setFieldKey(ITEM_KEY);
        col.setFieldName("编号");
        col.setFieldType("ITEM_KEY");
        col.setSystemField(true);
        col.setImportable(true);
        col.setDefaultSelected(true);
        return col;
    }

    private boolean isCoreField(String key) {
        return "title".equals(key) || "status".equals(key) || "priority".equals(key);
    }
}
