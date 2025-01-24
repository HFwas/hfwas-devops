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

    @Override
    public void insertFill(MetaObject metaObject) {
        Object createTime = getFieldValByName("createTime", metaObject);
        if (Objects.nonNull(createTime)) {
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (Objects.nonNull(updateTime)) {
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Object createTime = getFieldValByName("createTime", metaObject);
        if (Objects.nonNull(createTime)) {
            this.strictUpdateFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        }
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (Objects.nonNull(updateTime)) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
    }

}
