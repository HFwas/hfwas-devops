package com.hfwas.devops.repository;

import com.hfwas.devops.entity.User;

/**
 * @author houfei
 * @package com.hfwas.devops.repository
 * @date 2025/4/12
 */
public interface UserRepository {

    User findByUsername(String username);

}
