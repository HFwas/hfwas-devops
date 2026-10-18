package com.hfwas.devops.apitest.support;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.spi.ProjectAccessValidator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试用项目访问校验器。
 * <p>
 * 默认放行全部项目，避免影响存量用例；需要验证隔离时调用
 * {@link #allow(Long...)} 或 {@link #denyAll()} 收窄可见集合，
 * 用例结束调用 {@link #reset()} 恢复。
 *
 * @author hfwas
 */
public class TestProjectAccessValidator implements ProjectAccessValidator {

    private static final List<Long> DEFAULT_PROJECT_IDS =
            List.of(1001L, 1002L, 2001L, 2002L, 3001L, 5001L);

    private volatile boolean allowAll = true;
    private volatile Set<Long> accessibleProjects = Set.copyOf(DEFAULT_PROJECT_IDS);
    private volatile Long tenantId = 1L;

    @Override
    public void assertAccess(Long projectId) {
        if (!allowAll && !accessibleProjects.contains(projectId)) {
            throw new BizException(ResultCode.PROJECT_ACCESS_DENIED);
        }
    }

    @Override
    public List<Long> accessibleProjectIds() {
        if (allowAll) {
            return new ArrayList<>(DEFAULT_PROJECT_IDS);
        }
        return new ArrayList<>(accessibleProjects);
    }

    @Override
    public Long currentTenantId() {
        return tenantId;
    }

    public void allow(Long... projectIds) {
        this.allowAll = false;
        this.accessibleProjects = Set.copyOf(new LinkedHashSet<>(List.of(projectIds)));
    }

    public void denyAll() {
        this.allowAll = false;
        this.accessibleProjects = Set.of();
    }

    public void allowAll() {
        this.allowAll = true;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void reset() {
        this.allowAll = true;
        this.accessibleProjects = Set.copyOf(DEFAULT_PROJECT_IDS);
        this.tenantId = 1L;
    }
}
