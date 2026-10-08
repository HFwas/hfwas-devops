-- Chart 目录详情：从上传包抽出的 keywords / README / 默认 values。
-- content_cached=0 表示还没抽过，读取时再从 OCI 拉一次并写回。

ALTER TABLE helm_chart_artifact ADD COLUMN keywords TEXT NOT NULL DEFAULT '[]';
ALTER TABLE helm_chart_artifact ADD COLUMN readme TEXT NOT NULL DEFAULT '';
ALTER TABLE helm_chart_artifact ADD COLUMN values_yaml TEXT NOT NULL DEFAULT '';
ALTER TABLE helm_chart_artifact ADD COLUMN content_cached INTEGER NOT NULL DEFAULT 0;
