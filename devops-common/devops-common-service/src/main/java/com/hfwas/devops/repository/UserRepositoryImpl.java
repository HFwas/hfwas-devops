package com.hfwas.devops.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.entity.User;
import com.hfwas.devops.mapper.UserMapper;
import org.springframework.stereotype.Repository;

/**
 * @author houfei
 * @package com.hfwas.devops.repository
 * @date 2025/4/12
 */
@Repository
public class UserRepositoryImpl implements UserRepository{

    private final UserMapper userMapper;
    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User findByUsername(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username), false);
        return user;
    }
}
