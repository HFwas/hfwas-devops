package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysTenant;
import com.hfwas.devops.user.mapper.SysTenantMapper;
import com.hfwas.devops.user.model.TenantPageRequest;
import com.hfwas.devops.user.model.TenantSaveRequest;
import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.model.TenantVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{1,31}$");
    public static final long DEFAULT_TENANT_ID = 1L;

    private final SysTenantMapper tenantMapper;
    private final TenantMemberService tenantMemberService;
    private final JdbcTemplate jdbcTemplate;

    public IPage<TenantVO> page(TenantPageRequest request) {
        requirePlatformAdmin();
        int pageNo = request.resolvePageNo();
        int pageSize = request.resolvePageSize();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String status = StringUtils.defaultIfBlank(request.getStatus(), "all");

        Page<SysTenant> page = tenantMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<SysTenant>lambdaQuery()
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysTenant::getCode, keyword)
                                .or().like(SysTenant::getName, keyword)
                                .or().like(SysTenant::getContactName, keyword))
                        .eq("1".equals(status), SysTenant::getStatus, 1)
                        .eq("0".equals(status), SysTenant::getStatus, 0)
                        .orderByAsc(SysTenant::getId));
        return page.convert(this::toVo);
    }

    public List<TenantVO> options() {
        requirePlatformAdmin();
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                        .eq(SysTenant::getStatus, 1)
                        .orderByAsc(SysTenant::getName))
                .stream()
                .map(this::toVo)
                .toList();
    }

    /** All enabled tenants for platform admin tenant switching. */
    public List<TenantOptionVO> listEnabledOptions() {
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                        .eq(SysTenant::getStatus, 1)
                        .orderByAsc(SysTenant::getName))
                .stream()
                .map(t -> {
                    TenantOptionVO vo = new TenantOptionVO();
                    vo.setId(t.getId());
                    vo.setCode(t.getCode());
                    vo.setName(t.getName());
                    return vo;
                })
                .toList();
    }

    public TenantVO getById(Long id) {
        requirePlatformAdmin();
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }
        return toVo(tenant);
    }

    public SysTenant requireEnabled(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new IllegalArgumentException("租户已停用");
        }
        return tenant;
    }

    public SysTenant findByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getCode, code.trim().toLowerCase()));
    }

    @Transactional
    public Long save(TenantSaveRequest request) {
        requirePlatformAdmin();
        validateSaveRequest(request);
        String code = request.getCode().trim().toLowerCase();
        Long dup = tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getCode, code)
                .ne(request.getId() != null, SysTenant::getId, request.getId()));
        if (dup != null && dup > 0) {
            throw new IllegalArgumentException("租户编码已存在");
        }
        SysTenant tenant;
        if (request.getId() == null) {
            tenant = new SysTenant();
            tenant.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        } else {
            tenant = tenantMapper.selectById(request.getId());
            if (tenant == null) {
                throw new IllegalArgumentException("租户不存在");
            }
            if (DEFAULT_TENANT_ID == tenant.getId() && request.getStatus() != null && request.getStatus() != 1) {
                throw new IllegalArgumentException("默认租户不可停用");
            }
            if (request.getStatus() != null) {
                tenant.setStatus(request.getStatus());
            }
        }
        tenant.setCode(code);
        tenant.setName(request.getName().trim());
        tenant.setContactName(StringUtils.trimToNull(request.getContactName()));
        tenant.setContactPhone(StringUtils.trimToNull(request.getContactPhone()));
        tenant.setRemark(StringUtils.trimToNull(request.getRemark()));
        if (request.getId() == null) {
            tenantMapper.insert(tenant);
        } else {
            tenantMapper.updateById(tenant);
        }
        return tenant.getId();
    }

    @Transactional
    public void delete(Long id) {
        requirePlatformAdmin();
        if (id == null || DEFAULT_TENANT_ID == id) {
            throw new IllegalArgumentException("默认租户不可删除");
        }
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }
        long users = tenantMemberService.countMembers(id);
        if (users > 0) {
            throw new IllegalArgumentException("租户下仍有成员，无法删除");
        }
        Long projects = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pm_project WHERE tenant_id = ? AND del_flag = 0", Long.class, id);
        if (projects != null && projects > 0) {
            throw new IllegalArgumentException("租户下仍有项目，无法删除");
        }
        tenantMapper.deleteById(id);
    }

    private void validateSaveRequest(TenantSaveRequest request) {
        if (StringUtils.isBlank(request.getCode()) || StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("租户编码和名称不能为空");
        }
        String code = request.getCode().trim().toLowerCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("租户编码需以小写字母开头，仅含小写字母、数字、下划线或连字符");
        }
    }

    private TenantVO toVo(SysTenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setCode(tenant.getCode());
        vo.setName(tenant.getName());
        vo.setContactName(tenant.getContactName());
        vo.setContactPhone(tenant.getContactPhone());
        vo.setStatus(tenant.getStatus());
        vo.setRemark(tenant.getRemark());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateTime(tenant.getUpdateTime());
        vo.setUserCount(countUsers(tenant.getId()));
        vo.setProjectCount(countProjects(tenant.getId()));
        return vo;
    }

    private long countUsers(Long tenantId) {
        return tenantMemberService.countMembers(tenantId);
    }

    private long countProjects(Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pm_project WHERE tenant_id = ? AND del_flag = 0", Long.class, tenantId);
        return count == null ? 0 : count;
    }

    private void requirePlatformAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要平台管理员权限");
        }
    }
}
