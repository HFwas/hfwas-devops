package com.hfwas.devops.user.operlog.spi;

import com.hfwas.devops.user.operlog.model.OperLogEntry;

/**
 * SPI for recording operation audit logs from any business module.
 * Implemented by user-core; defaults to no-op when not on classpath.
 */
public interface OperLogRecorder {

    void record(OperLogEntry entry);
}
