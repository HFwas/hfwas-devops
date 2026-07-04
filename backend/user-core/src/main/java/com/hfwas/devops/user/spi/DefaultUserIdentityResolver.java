package com.hfwas.devops.user.spi;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultUserIdentityResolver implements UserIdentityResolver {

    private final SysUserMapper userMapper;

    @Override
    public Long resolveByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username.trim())
                .eq(SysUser::getDelFlag, 0)
                .last("LIMIT 1"));
        return user != null ? user.getId() : null;
    }

    @Override
    public Long resolveByDisplayName(String displayName) {
        if (StringUtils.isBlank(displayName)) {
            return null;
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getDisplayName, displayName.trim())
                .eq(SysUser::getDelFlag, 0)
                .last("LIMIT 1"));
        return user != null ? user.getId() : null;
    }

    @Override
    public String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return StringUtils.defaultIfBlank(user.getUsername(), user.getDisplayName());
    }
}
