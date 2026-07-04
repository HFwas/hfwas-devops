package com.hfwas.devops.pm.workitem.io;

public enum WorkItemImportMode {
    /** Always create new items. */
    CREATE,
    /** Update existing items matched by itemKey; create when not found. */
    UPSERT
}
