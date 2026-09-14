-- 迁移：pipeline_task_kind 追加 CPU/Memory 资源配置列
-- 仅对旧库生效，新库 CREATE TABLE 已包含
-- 幂等执行方式（SQLite 不支持 IF NOT EXISTS，需执行器保障）：
--   1. migrate.py（推荐）：自动捕获 duplicate column name 异常
--   2. migrate.sh（零依赖）：自动跳过已执行迁移
--   3. 直接 sqlite3：需外层 2>/dev/null 忽略错误
ALTER TABLE pipeline_task_kind ADD COLUMN cpu_request    TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind ADD COLUMN cpu_limit      TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind ADD COLUMN memory_request TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind ADD COLUMN memory_limit   TEXT NOT NULL DEFAULT '';