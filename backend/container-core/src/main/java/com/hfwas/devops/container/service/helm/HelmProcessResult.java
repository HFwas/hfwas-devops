package com.hfwas.devops.container.service.helm;

public record HelmProcessResult(int exitCode, String stdout, String stderr) {

    public String combined() {
        String out = stdout == null ? "" : stdout;
        String err = stderr == null ? "" : stderr;
        if (out.isBlank()) {
            return err;
        }
        if (err.isBlank()) {
            return out;
        }
        return out + "\n" + err;
    }
}
