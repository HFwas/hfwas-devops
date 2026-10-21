-- PM 阶段 1：截止时间、乐观锁、迭代类型、批量动作配置与关注者
SET NAMES utf8mb4;

-- 1. 工作项：截止时间与乐观锁版本
ALTER TABLE pm_work_item
    ADD COLUMN end_time DATETIME NULL AFTER sprint_id,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER update_time;

-- 2. 事项类型：内置标识、描述与模板
ALTER TABLE pm_work_item_type
    ADD COLUMN built_in TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN description VARCHAR(512) NULL,
    ADD COLUMN template_code VARCHAR(64) NULL;

-- 3. 字段定义：批量编辑属性
ALTER TABLE pm_field_definition
    ADD COLUMN batch_editable TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN batch_order INT NOT NULL DEFAULT 0;

-- 4. 活动：批量批次号改字符串，放宽变更值长度
ALTER TABLE pm_work_item_activity
    MODIFY COLUMN batch_id VARCHAR(64) NULL,
    MODIFY COLUMN old_value MEDIUMTEXT NULL,
    MODIFY COLUMN new_value MEDIUMTEXT NULL,
    MODIFY COLUMN old_label VARCHAR(1024) NULL,
    MODIFY COLUMN new_label VARCHAR(1024) NULL;

-- 5. 事项关注者
CREATE TABLE pm_work_item_watcher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_work_item_watcher_item_user (work_item_id, user_id),
    KEY idx_pm_work_item_watcher_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- 6. 项目批量动作配置
CREATE TABLE pm_batch_action_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    action_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    max_items INT NOT NULL DEFAULT 100,
    require_confirm TINYINT NOT NULL DEFAULT 1,
    allowed_roles MEDIUMTEXT,
    field_whitelist MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_batch_action_project_code (project_id, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- 7. 既有 4 个类型标记为内置并补描述
UPDATE pm_work_item_type SET built_in = 1, template_code = code WHERE code IN ('requirement', 'task', 'bug', 'test_case');
UPDATE pm_work_item_type SET description = '业务需求与用户价值' WHERE code = 'requirement';
UPDATE pm_work_item_type SET description = '可执行的工作单元' WHERE code = 'task';
UPDATE pm_work_item_type SET description = '缺陷修复与回归验证' WHERE code = 'bug';
UPDATE pm_work_item_type SET description = '测试场景与执行结果' WHERE code = 'test_case';

-- 8. 迭代类型（排在缺陷之后、测试用例之前）
UPDATE pm_work_item_type SET sort_order = 5 WHERE code = 'test_case';

INSERT INTO pm_work_item_type (id, code, name, icon, color, sort_order, enabled, built_in, description, template_code)
VALUES (5, 'iteration', '迭代', 'IterationCw', '#7c3aed', 4, 1, 1, '按周期规划交付范围的工作项集合', 'iteration');

-- 9. 迭代默认工作流（复用 V2 中未使用的状态 id 5 / 10 / 15 / 20）
INSERT INTO pm_status_definition
    (id, project_id, type_code, status_code, status_name, sort_order, is_initial, is_final, transitions)
VALUES
    (5, NULL, 'iteration', 'open', '待处理', 1, 1, 0,
     '[{"id":"iteration-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"iteration-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
    (10, NULL, 'iteration', 'in_progress', '进行中', 2, 0, 0,
     '[{"id":"iteration-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"iteration-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
    (15, NULL, 'iteration', 'done', '已完成', 3, 0, 0,
     '[{"id":"iteration-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
    (20, NULL, 'iteration', 'closed', '已关闭', 4, 0, 1, '[]');

-- 10. 索引
CREATE INDEX idx_pm_work_item_end_time ON pm_work_item (project_id, end_time);
CREATE INDEX idx_pm_work_item_activity_batch ON pm_work_item_activity (batch_id);
