package com.hfwas.devops.user.operlog.annotation;

import java.lang.annotation.*;

/**
 * Declarative operation audit log (RuoYi / GitLab Audit Events style).
 * Processed by {@code OperLogAspect} in user-core after successful method execution.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** Module code, e.g. user, pm */
    String module();

    /** Action code, e.g. create, update, delete, save, transition, revoke */
    String action();

    /** Business entity type, e.g. user, project, work_item */
    String bizType() default "";

    /** Human-readable summary */
    String summary();

    /**
     * SpEL for business id, e.g. {@code #id}, {@code #result.data}, {@code #project.id}.
     * Empty = auto-detect from {@code id} param or {@code BaseResult.data}.
     */
    String bizId() default "";
}
