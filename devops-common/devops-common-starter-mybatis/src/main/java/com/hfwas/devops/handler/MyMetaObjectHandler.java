package com.hfwas.devops.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
* @package com.hfwas.devops.handler
* @author houfei
* @date  2025/1/24
*/
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final Long SYSTEM = 111111L;

    @Override
    public void insertFill(MetaObject metaObject) {
        Object createTime = getFieldValByName("createTime", metaObject);
        if (Objects.isNull(createTime)) {
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object createBy = getFieldValByName("createBy", metaObject);
        if (Objects.isNull(createBy)) {
            this.strictInsertFill(metaObject, "createBy", Long.class, SYSTEM);
        }
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (Objects.isNull(updateTime)) {
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object updateBy = getFieldValByName("updateBy", metaObject);
        if (Objects.isNull(updateBy)) {
            this.strictInsertFill(metaObject, "updateBy", Long.class, SYSTEM);
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
            this.strictInsertFill(metaObject, "updateBy", Long.class, SYSTEM);
        }
    }

}
