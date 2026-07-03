package com.hfwas.devops.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final Long SYSTEM = 111111L;

    private final CurrentUserAccessor currentUserAccessor;

    @Override
    public void insertFill(MetaObject metaObject) {
        Object createTime = getFieldValByName("createTime", metaObject);
        if (Objects.isNull(createTime)) {
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object createBy = getFieldValByName("createBy", metaObject);
        if (Objects.isNull(createBy)) {
            this.strictInsertFill(metaObject, "createBy", Long.class, resolveUserId());
        }
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (Objects.isNull(updateTime)) {
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object updateBy = getFieldValByName("updateBy", metaObject);
        if (Objects.isNull(updateBy)) {
            this.strictInsertFill(metaObject, "updateBy", Long.class, resolveUserId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (Objects.isNull(updateTime)) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object updateBy = getFieldValByName("updateBy", metaObject);
        if (Objects.isNull(updateBy)) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, resolveUserId());
        }
    }

    private Long resolveUserId() {
        Long userId = currentUserAccessor.currentUserId();
        return userId != null ? userId : SYSTEM;
    }
}
