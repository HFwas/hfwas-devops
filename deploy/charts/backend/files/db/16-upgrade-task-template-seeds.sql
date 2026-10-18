-- ============================================================
-- 16-upgrade-task-template-seeds.sql
--
-- 历史库的显式种子升级。05 已改为零 UPDATE，本文件是唯一允许
-- UPDATE 的迁移，只匹配“旧默认值”，不触碰用户自定义值。
--
-- 旧值来源：git show b5c6bf0^:deploy/charts/backend/files/db/05-pipeline-schema.sql
--  - DEPENDENCY_ANALYSIS.default_command 旧值精确包含 " -q"
--  - DEPENDENCY_TRACK.DT_FAIL_ON.options_json 旧值为 '[]'
-- 空 command_template 视为用户主动选择 DEFAULT_TEMPLATE，不在此处填充；
-- 05 的旧模板已经由历史 UPDATE 写入，无需在 16-* 重复升级。
-- ============================================================

-- 0) 旧库补列（幂等：runner 会跳过 duplicate column）。
ALTER TABLE pipeline_task_kind ADD COLUMN command_template TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind_param ADD COLUMN options_json TEXT NOT NULL DEFAULT '[]';

-- 1) 依赖分析默认命令去掉 -q，便于流水线日志可见。
UPDATE pipeline_task_kind
SET default_command = 'mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress'
WHERE kind_value = 'DEPENDENCY_ANALYSIS'
  AND default_command = 'mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress -q';

-- 2) DT_FAIL_ON 选项：只升级仍为空默认值的行。
UPDATE pipeline_task_kind_param
SET options_json = '[{"value":"none","label":"不检查（仅记录）"},{"value":"critical","label":"Critical 以上"},{"value":"high","label":"High 以上"},{"value":"medium","label":"Medium 以上"},{"value":"low","label":"Low 以上"}]'
WHERE kind_value = 'DEPENDENCY_TRACK'
  AND param_key = 'DT_FAIL_ON'
  AND (options_json IS NULL OR options_json = '' OR options_json = '[]');
