package com.hfwas.devops.user.spi;

public enum NoOpUserIdentityResolver implements UserIdentityResolver {
    INSTANCE;

    @Override
    public Long resolveByUsername(String username) {
        return null;
    }

    @Override
    public Long resolveByDisplayName(String displayName) {
        return null;
    }
}
