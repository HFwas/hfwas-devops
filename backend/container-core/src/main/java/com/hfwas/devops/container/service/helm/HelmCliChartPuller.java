package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code helm registry login} then {@code helm pull}. Password stays on stdin.
 * The caller owns {@code destinationDir} and deletes it.
 */
@Component
@RequiredArgsConstructor
public class HelmCliChartPuller implements HelmChartPackagePuller {

    private final HelmProcessRunner processRunner;
    private final HelmChartProperties properties;

    @Override
    public Path pull(OciPullRequest request) {
        HelmChartRef ref = HelmChartRef.parse(request.chartRef());
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        if (!username.isEmpty() && password.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID, "OCI 用户名已配置但密码为空");
        }
        if (password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID, "OCI 密码不能包含换行");
        }
        String binary = binary();
        Path home = request.destinationDir();
        try {
            Files.createDirectories(home.resolve("config/helm/registry"));
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_PULL_FAILED, "无法准备 helm 配置目录");
        }
        var env = java.util.Map.of(
                "HOME", home.toString(),
                "HELM_CACHE_HOME", home.resolve("cache").toString(),
                "HELM_CONFIG_HOME", home.resolve("config").toString(),
                "HELM_DATA_HOME", home.resolve("data").toString(),
                "HELM_REGISTRY_CONFIG", home.resolve("config/helm/registry/config.json").toString());
        if (!username.isEmpty()) {
            HelmProcessResult login = processRunner.run(HelmProcessCommand.captured(
                    loginCommand(binary, ref.host(), username, request.plainHttp()),
                    password.getBytes(StandardCharsets.UTF_8),
                    env,
                    home,
                    request.timeout()));
            if (login.exitCode() != 0) {
                throw failed(password, login);
            }
        }
        HelmProcessResult pulled = processRunner.run(HelmProcessCommand.captured(
                pullCommand(binary, ref, home, request.plainHttp()),
                null,
                env,
                home,
                request.timeout()));
        if (pulled.exitCode() != 0) {
            throw failed(password, pulled);
        }
        return findArchive(home);
    }

    static List<String> loginCommand(String binary, String host, String username, boolean plainHttp) {
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        argv.add("registry");
        argv.add("login");
        argv.add(host);
        argv.add("--username");
        argv.add(username);
        argv.add("--password-stdin");
        if (plainHttp) {
            argv.add("--insecure");
        }
        return List.copyOf(argv);
    }

    static List<String> pullCommand(String binary, HelmChartRef ref, Path destination, boolean plainHttp) {
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        argv.add("pull");
        argv.add(ref.locator());
        argv.add("--version");
        argv.add(ref.version());
        argv.add("--destination");
        argv.add(destination.toString());
        if (plainHttp) {
            argv.add("--plain-http");
        }
        return List.copyOf(argv);
    }

    private Path findArchive(Path home) {
        try (Stream<Path> files = Files.list(home)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".tgz"))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ContainerErrorCode.HELM_CHART_PULL_FAILED, "helm pull 没有产生 Chart 包"));
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_PULL_FAILED, "无法读取 helm pull 结果");
        }
    }

    private String binary() {
        String configured = properties.getHelmBinary();
        return configured == null || configured.isBlank() ? "helm" : configured;
    }

    private static BizException failed(String password, HelmProcessResult result) {
        String detail = HelmFailureFormatter.summarize(result, password);
        if (detail.isBlank()) {
            detail = "helm 退出码 " + result.exitCode();
        }
        return new BizException(ContainerErrorCode.HELM_CHART_PULL_FAILED, "拉取 Helm Chart 失败: " + detail);
    }
}
