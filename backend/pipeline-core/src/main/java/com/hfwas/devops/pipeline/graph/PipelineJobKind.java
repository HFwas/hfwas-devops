package com.hfwas.devops.pipeline.graph;

public enum PipelineJobKind {
    CLONE("克隆", false, null),
    LINT("代码检查", true, """
            export LINT_SEMGREP_ARGS="scan --error --config=auto ."
            # export SONAR_HOST_URL=https://sonar.example.com
            # export SONAR_TOKEN=
            # export SONAR_PROJECT_KEY=app
            """),
    BUILD("构建", true, null),
    TEST("测试", true, null),
    SCAN("安全扫描", true, "trivy fs --exit-code 1 --scanners vuln,secret,misconfig ."),
    PACKAGE("打包", true, null),
    CUSTOM("自定义", true, "echo ok"),
    IMAGE("镜像构建", true, """
            export DEST=registry.example.com/app:tag
            export IMAGE_PLATFORMS=linux/amd64
            export DOCKERFILE=Dockerfile
            """),
    PUBLISH("发布制品", true, null),
    UPLOAD("上传对象存储", true,
            "rclone copy ./ :s3:bucket/prefix --s3-provider=Minio --s3-endpoint=\"${S3_ENDPOINT}\" --s3-access-key-id=\"${S3_ACCESS_KEY}\" --s3-secret-access-key=\"${S3_SECRET_KEY}\""),
    DEPLOY("部署", true, "kubectl apply -f k8s/"),
    APPROVAL("人工卡点", false, null),
    NOTIFY("通知", true,
            "curl -fsS -X POST 'https://example.com/hook' -H 'Content-Type: application/json' -d '{\"status\":\"done\"}'");

    private final String label;
    private final boolean requiresCommand;
    private final String defaultCommand;

    PipelineJobKind(String label, boolean requiresCommand, String defaultCommand) {
        this.label = label;
        this.requiresCommand = requiresCommand;
        this.defaultCommand = defaultCommand;
    }

    public String label() {
        return label;
    }

    public boolean requiresCommand() {
        return requiresCommand;
    }

    public String defaultCommand() {
        return defaultCommand;
    }
}
