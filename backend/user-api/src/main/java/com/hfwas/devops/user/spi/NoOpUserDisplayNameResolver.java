package com.hfwas.devops.user.spi;

public enum NoOpUserDisplayNameResolver implements UserDisplayNameResolver {
    INSTANCE;

    @Override
    public String resolve(Long userId) {
        return userId == null ? null : String.valueOf(userId);
    }
}
