package com.hfwas.devops.container.service.helm;

public record OciPushResult(String chartRef, String digest) {
}
