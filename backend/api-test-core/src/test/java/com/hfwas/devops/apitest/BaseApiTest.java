package com.hfwas.devops.apitest;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 接口测试平台 — 基础测试父类
 * <p>
 * 所有测试类应继承此类，统一管理：
 * - SQLite 内存数据库初始化
 * - 事务管理（每个测试方法独立事务，自动回滚）
 * - MockMvc 注入
 * - Jackson ObjectMapper 注入
 * - 公共断言方法
 *
 * @author hfwas
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
@Rollback
@AutoConfigureMockMvc
@Import({BaseApiTest.TestConfig.class})
public abstract class BaseApiTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 测试环境下的 MyBatis-Plus 自动填充处理器
     * 默认填充 createBy=1L, updateBy=1L, createTime=now, updateTime=now
     */
    @Configuration
    static class TestConfig {

        @Bean
        public MetaObjectHandler metaObjectHandler() {
            return new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject metaObject) {
                    this.strictInsertFill(metaObject, "createBy", Long.class, 1L);
                    this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                    this.strictInsertFill(metaObject, "updateBy", Long.class, 1L);
                    this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                }

                @Override
                public void updateFill(MetaObject metaObject) {
                    this.strictUpdateFill(metaObject, "updateBy", Long.class, 1L);
                    this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                }
            };
        }
    }

    // ========== 公共断言辅助方法 ==========

    /**
     * 将任意对象转为 JSON 字符串
     */
    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串转为指定类型
     */
    protected <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * 获取测试用的项目ID
     */
    protected Long testProjectId() {
        return 1001L;
    }

    /**
     * 获取测试用的用户ID
     */
    protected Long testUserId() {
        return 1L;
    }
}