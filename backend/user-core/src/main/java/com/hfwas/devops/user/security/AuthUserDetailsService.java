package com.hfwas.devops.user.security;

import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService {

    private final SysUserMapper userMapper;

    public AuthUserPrincipal loadById(Long id) {
        return loadById(id, null);
    }

    public AuthUserPrincipal loadById(Long id, Long loginTenantId) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new AuthUserPrincipal(user, loginTenantId);
    }
}
