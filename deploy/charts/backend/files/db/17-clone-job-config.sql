-- ============================================================
-- 17: 代码克隆任务参数种子
-- ============================================================
-- 背景：流水线级 repo_url / git_ref / credential_id 改为由 CLONE 任务的
--       param_bindings（REPO_URL / GIT_REF / CREDENTIAL_ID）派生。
-- 本迁移只补充任务市场参数种子。
-- 旧克隆任务的绑定回填由应用启动时的 CloneConfigBackfill 完成，
-- 以保持迁移脚本零 UPDATE 的安全策略。
-- ============================================================

INSERT OR IGNORE INTO pipeline_task_kind_param (
    id, kind_value, param_key, param_label, param_type, default_value, required, sort_order, placeholder, options_json
) VALUES
(6, 'CLONE', 'REPO_URL',      '仓库地址', 'input', '', 1, 0, 'https://github.com/org/repo.git', '[]'),
(7, 'CLONE', 'CREDENTIAL_ID', '凭证',     'input', '', 0, 2, '', '[]');
