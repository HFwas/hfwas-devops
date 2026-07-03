package com.hfwas.devops.user.operlog.spi;

import com.hfwas.devops.user.operlog.model.OperLogEntry;

/** No-op fallback for tests or when user-core is absent. */
public final class NoOpOperLogRecorder implements OperLogRecorder {

    public static final NoOpOperLogRecorder INSTANCE = new NoOpOperLogRecorder();

    private NoOpOperLogRecorder() {
    }

    @Override
    public void record(OperLogEntry entry) {
        // no-op
    }
}
