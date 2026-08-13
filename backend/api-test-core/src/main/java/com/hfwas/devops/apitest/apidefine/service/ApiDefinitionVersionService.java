package com.hfwas.devops.apitest.apidefine.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionVersionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 接口版本记录业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionVersionService
        extends ServiceImpl<ApiDefinitionVersionMapper, ApiDefinitionVersionEntity> {

    /**
     * 创建版本快照（发布时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void createSnapshot(ApiDefinitionEntity entity, Long userId) {
        // 版本号递增
        String newVersion = incrementVersion(entity.getVersion());

        ApiDefinitionVersionEntity versionEntity = new ApiDefinitionVersionEntity();
        versionEntity.setDefinitionId(entity.getId());
        versionEntity.setVersion(newVersion);
        versionEntity.setChangeLog("发布版本 " + newVersion);
        versionEntity.setSnapshotName(entity.getName());
        versionEntity.setSnapshotPath(entity.getPath());
        versionEntity.setSnapshotMethod(entity.getMethod());
        versionEntity.setSnapshotDescription(entity.getDescription());
        save(versionEntity);

        // 更新主表版本号
        entity.setVersion(newVersion);
    }

    /**
     * 获取版本历史
     */
    public List<ApiDefinitionVersionEntity> getVersionHistory(Long definitionId) {
        return lambdaQuery()
                .eq(ApiDefinitionVersionEntity::getDefinitionId, definitionId)
                .orderByDesc(ApiDefinitionVersionEntity::getCreateTime)
                .list();
    }

    /**
     * 版本号递增（1.0.0 → 1.0.1 → 1.0.2）
     */
    private String incrementVersion(String currentVersion) {
        if (currentVersion == null) {
            return "1.0.0";
        }
        try {
            String[] parts = currentVersion.split("\\.");
            int patch = Integer.parseInt(parts[parts.length - 1]) + 1;
            parts[parts.length - 1] = String.valueOf(patch);
            return String.join(".", parts);
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}