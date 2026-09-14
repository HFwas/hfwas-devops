-- 迁移：pipeline_job 追加 param_bindings 列
-- 10-add-runtime-params.sql 中此 ALTER TABLE 因旧库 CREATE TABLE 未包含而未生效
-- 仅对旧库生效，新库 CREATE TABLE 已包含
-- 幂等执行方式（SQLite 不支持 IF NOT EXISTS，需执行器保障）：
--   1. migrate.py（推荐）：自动捕获 duplicate column name 异常
--   2. migrate.sh（零依赖）：自动跳过已执行迁移
--   3. 直接 sqlite3：需外层 2>/dev/null 忽略错误
ALTER TABLE pipeline_job ADD COLUMN param_bindings TEXT NOT NULL DEFAULT '{}';