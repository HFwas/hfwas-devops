package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.entity.SysTenant;
import com.hfwas.devops.user.entity.SysTenantMember;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysTenantMapper;
import com.hfwas.devops.user.mapper.SysTenantMemberMapper;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.model.TenantMemberAddRequest;
import com.hfwas.devops.user.model.TenantMemberPageRequest;
import com.hfwas.devops.user.model.TenantMemberSaveRequest;
import com.hfwas.devops.user.model.TenantMemberVO;
import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.message.SiteMessageNotifier;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantMemberService {

    private final SysTenantMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final SiteMessageNotifier messageNotifier;

    public IPage<TenantMemberVO> page(Long tenantId, TenantMemberPageRequest request) {
        requireEnabledTenant(tenantId);
        int pageNo = request.resolvePageNo();
        int pageSize = request.resolvePageSize();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        List<Long> userIdFilter = null;
        if (StringUtils.isNotBlank(keyword)) {
            userIdFilter = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .and(w -> w.like(SysUser::getUsername, keyword)
                                    .or().like(SysUser::getDisplayName, keyword)
                                    .or().like(SysUser::getEmail, keyword))
                            .select(SysUser::getId))
                    .stream()
                    .map(SysUser::getId)
                    .toList();
            if (userIdFilter.isEmpty()) {
                return new Page<>(pageNo, pageSize, 0);
            }
        }

        Page<SysTenantMember> page = memberMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .in(userIdFilter != null, SysTenantMember::getUserId, userIdFilter)
                        .orderByDesc(SysTenantMember::getJoinTime));

        List<SysTenantMember> records = page.getRecords();
        Map<Long, SysUser> userMap = loadUsers(records.stream().map(SysTenantMember::getUserId).toList());
        List<TenantMemberVO> vos = new ArrayList<>();
        for (SysTenantMember member : records) {
            SysUser user = userMap.get(member.getUserId());
            if (user != null) {
                vos.add(toVo(member, user));
            }
        }
        Page<TenantMemberVO> result = new Page<>(pageNo, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    public List<UserProfileLite> listAvailable(Long tenantId, String keyword) {
        requireEnabledTenant(tenantId);
        List<Long> memberUserIds = memberMapper.selectList(Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .select(SysTenantMember::getUserId))
                .stream()
                .map(SysTenantMember::getUserId)
                .toList();
        String k = StringUtils.trimToEmpty(keyword);
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getEnabled, 1)
                        .notIn(!memberUserIds.isEmpty(), SysUser::getId, memberUserIds)
                        .and(StringUtils.isNotBlank(k), w -> w
                                .like(SysUser::getUsername, k)
                                .or().like(SysUser::getDisplayName, k)
                                .or().like(SysUser::getEmail, k))
                        .orderByAsc(SysUser::getDisplayName))
                .stream()
                .map(u -> new UserProfileLite(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail()))
                .toList();
    }

    @Transactional
    public void addMembers(Long tenantId, TenantMemberAddRequest request) {
        requireEnabledTenant(tenantId);
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new IllegalArgumentException("请选择要加入的用户");
        }
        String role = normalizeTenantRole(request.getTenantRole());
        for (Long userId : request.getUserIds()) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
                throw new IllegalArgumentException("用户不存在或已禁用: " + userId);
            }
            SysTenantMember existing = memberMapper.selectOne(Wrappers.<SysTenantMember>lambdaQuery()
                    .eq(SysTenantMember::getTenantId, tenantId)
                    .eq(SysTenantMember::getUserId, userId));
            if (existing != null) {
                existing.setStatus(1);
                existing.setTenantRole(role);
                memberMapper.updateById(existing);
            } else {
                SysTenantMember member = new SysTenantMember();
                member.setTenantId(tenantId);
                member.setUserId(userId);
                member.setTenantRole(role);
                member.setStatus(1);
                memberMapper.insert(member);
            }
            if (tenant != null) {
                messageNotifier.notifyTenantJoined(userId, tenantId, tenant.getName());
            }
        }
    }

    @Transactional
    public void saveMember(Long tenantId, TenantMemberSaveRequest request) {
        requireEnabledTenant(tenantId);
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        SysTenantMember member = memberMapper.selectOne(Wrappers.<SysTenantMember>lambdaQuery()
                .eq(SysTenantMember::getTenantId, tenantId)
                .eq(SysTenantMember::getUserId, request.getUserId()));
        if (member == null) {
            throw new IllegalArgumentException("该用户不是租户成员");
        }
        if (StringUtils.isNotBlank(request.getTenantRole())) {
            member.setTenantRole(normalizeTenantRole(request.getTenantRole()));
        }
        if (request.getStatus() != null) {
            member.setStatus(request.getStatus());
        }
        memberMapper.updateById(member);
    }

    @Transactional
    public void removeMember(Long tenantId, Long userId) {
        requireEnabledTenant(tenantId);
        SysTenant tenant = tenantMapper.selectById(tenantId);
        memberMapper.delete(Wrappers.<SysTenantMember>lambdaQuery()
                .eq(SysTenantMember::getTenantId, tenantId)
                .eq(SysTenantMember::getUserId, userId));
        if (tenant != null) {
            messageNotifier.notifyTenantRemoved(userId, tenant.getName());
        }
    }

    public boolean isActiveMember(Long tenantId, Long userId) {
        SysTenantMember member = memberMapper.selectOne(Wrappers.<SysTenantMember>lambdaQuery()
                .eq(SysTenantMember::getTenantId, tenantId)
                .eq(SysTenantMember::getUserId, userId)
                .eq(SysTenantMember::getStatus, 1));
        return member != null;
    }

    public List<SysUser> listEnabledMembers(Long tenantId) {
        List<SysTenantMember> members = memberMapper.selectList(Wrappers.<SysTenantMember>lambdaQuery()
                .eq(SysTenantMember::getTenantId, tenantId)
                .eq(SysTenantMember::getStatus, 1)
                .orderByAsc(SysTenantMember::getUserId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = members.stream().map(SysTenantMember::getUserId).toList();
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getId, userIds)
                .eq(SysUser::getEnabled, 1));
        Map<Long, SysUser> userMap = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        List<SysUser> ordered = new ArrayList<>();
        for (SysTenantMember member : members) {
            SysUser user = userMap.get(member.getUserId());
            if (user != null) {
                ordered.add(user);
            }
        }
        ordered.sort(Comparator.comparing(SysUser::getDisplayName, Comparator.nullsLast(String::compareTo)));
        return ordered;
    }

    public long countMembers(Long tenantId) {
        Long count = memberMapper.selectCount(Wrappers.<SysTenantMember>lambdaQuery()
                .eq(SysTenantMember::getTenantId, tenantId)
                .eq(SysTenantMember::getStatus, 1));
        return count == null ? 0 : count;
    }

    public List<Long> listUserIdsByTenant(Long tenantId) {
        return memberMapper.selectList(Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .select(SysTenantMember::getUserId))
                .stream()
                .map(SysTenantMember::getUserId)
                .toList();
    }

    public List<TenantOptionVO> listEnabledTenantsByUser(Long userId) {
        List<Long> tenantIds = memberMapper.selectList(Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getUserId, userId)
                        .eq(SysTenantMember::getStatus, 1)
                        .select(SysTenantMember::getTenantId))
                .stream()
                .map(SysTenantMember::getTenantId)
                .toList();
        if (tenantIds.isEmpty()) {
            return List.of();
        }
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                        .in(SysTenant::getId, tenantIds)
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

    public List<String> listTenantNamesByUser(Long userId) {
        List<Long> tenantIds = memberMapper.selectList(Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getUserId, userId)
                        .eq(SysTenantMember::getStatus, 1)
                        .select(SysTenantMember::getTenantId))
                .stream()
                .map(SysTenantMember::getTenantId)
                .toList();
        if (tenantIds.isEmpty()) {
            return List.of();
        }
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                        .in(SysTenant::getId, tenantIds)
                        .orderByAsc(SysTenant::getName))
                .stream()
                .map(SysTenant::getName)
                .toList();
    }

    public void ensureMember(Long tenantId, Long userId, String tenantRole) {
        if (isActiveMember(tenantId, userId)) {
            return;
        }
        SysTenantMember member = memberMapper.selectOne(Wrappers.<SysTenantMember>lambdaQuery()
                .eq(SysTenantMember::getTenantId, tenantId)
                .eq(SysTenantMember::getUserId, userId));
        if (member != null) {
            member.setStatus(1);
            member.setTenantRole(normalizeTenantRole(tenantRole));
            memberMapper.updateById(member);
            return;
        }
        member = new SysTenantMember();
        member.setTenantId(tenantId);
        member.setUserId(userId);
        member.setTenantRole(normalizeTenantRole(tenantRole));
        member.setStatus(1);
        memberMapper.insert(member);
    }

    private Map<Long, SysUser> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, userIds))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private TenantMemberVO toVo(SysTenantMember member, SysUser user) {
        TenantMemberVO vo = new TenantMemberVO();
        vo.setId(member.getId());
        vo.setTenantId(member.getTenantId());
        vo.setUserId(member.getUserId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setEmail(user.getEmail());
        vo.setTenantRole(member.getTenantRole());
        vo.setStatus(member.getStatus());
        vo.setJoinTime(member.getJoinTime());
        return vo;
    }

    private String normalizeTenantRole(String role) {
        String r = StringUtils.defaultIfBlank(role, "member");
        if (!List.of("tenant_admin", "member").contains(r)) {
            throw new IllegalArgumentException("无效的租户角色");
        }
        return r;
    }

    private void requireEnabledTenant(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new IllegalArgumentException("租户已停用");
        }
    }

    public record UserProfileLite(Long id, String username, String displayName, String email) {}
}
