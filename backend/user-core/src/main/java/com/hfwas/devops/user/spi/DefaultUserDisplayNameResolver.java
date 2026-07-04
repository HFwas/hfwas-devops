package com.hfwas.devops.user.spi;

import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultUserDisplayNameResolver implements UserDisplayNameResolver {

    private final SysUserMapper userMapper;

    @Override
    public String resolve(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
    }
}
