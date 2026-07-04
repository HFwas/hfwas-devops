package com.hfwas.devops.pm.field.model;

public enum FieldSchemeImportMode {
    /** Upsert custom fields and apply layout; keep extra fields on type. */
    MERGE,
    /** Upsert custom fields, apply layout, remove type-bound custom fields not in import. */
    REPLACE
}
