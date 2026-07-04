package com.hfwas.devops.user.spi;

/**
 * Resolves user display names for cross-module use (e.g. PM activity logs).
 */
public interface UserDisplayNameResolver {

    String resolve(Long userId);

}
