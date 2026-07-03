package com.hfwas.devops.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.service.WorkItemSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class PmItemNoMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PmWorkItemMapper workItemMapper;
    private final WorkItemSequenceService sequenceService;

    @Override
    public void run(ApplicationArguments args) {
        ensureModuleIdColumn();
        ensureItemNoColumn();
        ensureSequenceTable();
        backfillItemNo();
        ensureUniqueIndex();
        syncAllProjectSequences();
    }

    private void ensureModuleIdColumn() {
        if (hasColumn("pm_work_item", "module_id")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE pm_work_item ADD COLUMN module_id INTEGER");
        log.info("Added pm_work_item.module_id column");
    }

    private void ensureItemNoColumn() {
        if (hasColumn("pm_work_item", "item_no")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE pm_work_item ADD COLUMN item_no INTEGER");
        log.info("Added pm_work_item.item_no column");
    }

    private void ensureSequenceTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pm_project_sequence (
                    project_id    INTEGER NOT NULL PRIMARY KEY,
                    next_item_no  INTEGER NOT NULL DEFAULT 1
                )
                """);
    }

    private void backfillItemNo() {
        List<PmWorkItem> pending = workItemMapper.selectList(
                Wrappers.<PmWorkItem>lambdaQuery()
                        .isNull(PmWorkItem::getItemNo)
                        .orderByAsc(PmWorkItem::getProjectId)
                        .orderByAsc(PmWorkItem::getCreateTime)
                        .orderByAsc(PmWorkItem::getId)
        );
        if (pending.isEmpty()) {
            return;
        }

        Map<Long, List<PmWorkItem>> grouped = new LinkedHashMap<>();
        for (PmWorkItem item : pending) {
            grouped.computeIfAbsent(item.getProjectId(), key -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<Long, List<PmWorkItem>> entry : grouped.entrySet()) {
            Long projectId = entry.getKey();
            int next = resolveNextItemNo(projectId);
            for (PmWorkItem item : entry.getValue()) {
                item.setItemNo(next++);
                workItemMapper.updateById(item);
            }
        }

        log.info("Backfilled item_no for {} work items", pending.size());
    }

    private int resolveNextItemNo(Long projectId) {
        PmWorkItem latest = workItemMapper.selectOne(
                Wrappers.<PmWorkItem>lambdaQuery()
                        .eq(PmWorkItem::getProjectId, projectId)
                        .isNotNull(PmWorkItem::getItemNo)
                        .orderByDesc(PmWorkItem::getItemNo)
                        .last("LIMIT 1")
        );
        return latest == null || latest.getItemNo() == null ? 1 : latest.getItemNo() + 1;
    }

    private void ensureUniqueIndex() {
        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_pm_work_item_project_item_no ON pm_work_item(project_id, item_no)"
        );
    }

    private void syncAllProjectSequences() {
        List<Long> projectIds = jdbcTemplate.query(
                "SELECT DISTINCT project_id FROM pm_work_item WHERE del_flag = 0",
                (rs, rowNum) -> rs.getLong("project_id")
        );
        for (Long projectId : projectIds) {
            sequenceService.syncSequence(projectId);
        }
    }

    private boolean hasColumn(String table, String column) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        return rows.stream().anyMatch(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))));
    }
}
