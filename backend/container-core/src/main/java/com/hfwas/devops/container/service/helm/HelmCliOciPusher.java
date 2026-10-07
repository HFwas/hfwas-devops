package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.error.HelmChartConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code helm registry login} + {@code helm show chart} + {@code helm push}.
 * Registry config is written under a temporary directory and removed afterwards.
 * The password is passed on stdin and is never placed in argv.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HelmCliOciPusher implements HelmChartOciPusher {

    private final HelmProcessRunner processRunner;
    private final HelmChartProperties properties;

    @Override
    public OciPushResult push(OciPushRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        if (!username.isEmpty() && password.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID,
                    "OCI 用户名已配置但密码为空");
        }
        if (password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID, "OCI 密码不能包含换行");
        }
        Duration timeout = Duration.ofSeconds(Math.max(5, properties.getPushTimeoutSeconds()));
        String binary = properties.getHelmBinary() == null || properties.getHelmBinary().isBlank()
                ? "helm" : properties.getHelmBinary();
        Path home;
        try {
            home = Files.createTempDirectory("helm-oci-");
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "无法创建 helm 临时目录");
        }
        try {
            Map<String, String> env = Map.of(
                    "XDG_CONFIG_HOME", home.resolve("config").toString(),
                    "XDG_CACHE_HOME", home.resolve("cache").toString(),
                    "XDG_DATA_HOME", home.resolve("data").toString(),
                    "HELM_REGISTRY_CONFIG", home.resolve("config/helm/registry/config.json").toString()
            );
            Files.createDirectories(home.resolve("config/helm/registry"));
            if (!username.isEmpty()) {
                HelmProcessResult login = processRunner.run(new HelmProcessCommand(
                        loginCommand(binary, request),
                        password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        env,
                        home,
                        timeout));
                if (login.exitCode() != 0) {
                    throw pushFailed(password, login);
                }
            }
            HelmProcessResult show = processRunner.run(new HelmProcessCommand(
                    showCommand(binary, request),
                    null,
                    env,
                    home,
                    timeout));
            if (show.exitCode() == 0) {
                throw new HelmChartConflictException("OCI 仓库已存在该 Chart 版本，拒绝覆盖");
            }
            if (!isMissing(show)) {
                throw pushFailed(password, show);
            }
            HelmProcessResult pushed = processRunner.run(new HelmProcessCommand(
                    pushCommand(binary, request),
                    null,
                    env,
                    home,
                    timeout));
            if (pushed.exitCode() != 0) {
                throw pushFailed(password, pushed);
            }
            String digest = parseDigest(pushed.stdout() + "\n" + pushed.stderr());
            String chartRef = request.reference().chartRef(request.chartName(), request.version());
            log.info("Pushed helm chart {}", chartRef);
            return new OciPushResult(chartRef, digest);
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "准备 helm 配置目录失败");
        } finally {
            deleteRecursively(home);
        }
    }

    static List<String> loginCommand(String binary, OciPushRequest request) {
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        argv.add("registry");
        argv.add("login");
        argv.add(request.reference().host());
        argv.add("--username");
        argv.add(request.username());
        argv.add("--password-stdin");
        if (request.reference().plainHttp()) {
            argv.add("--insecure");
        }
        return argv;
    }

    static List<String> showCommand(String binary, OciPushRequest request) {
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        argv.add("show");
        argv.add("chart");
        argv.add(request.reference().chartLocator(request.chartName()));
        argv.add("--version");
        argv.add(request.version());
        if (request.reference().plainHttp()) {
            argv.add("--plain-http");
        }
        return argv;
    }

    static List<String> pushCommand(String binary, OciPushRequest request) {
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        argv.add("push");
        argv.add(request.archive().toString());
        argv.add(request.reference().registryBase());
        if (request.reference().plainHttp()) {
            argv.add("--plain-http");
        }
        return argv;
    }

    static boolean isMissing(HelmProcessResult result) {
        String blob = result.combined().toLowerCase(Locale.ROOT);
        if (blob.contains("unauthorized") || blob.contains("forbidden")
                || blob.contains("401") || blob.contains("403")) {
            return false;
        }
        return blob.contains("not found")
                || blob.contains("404")
                || blob.contains("manifest unknown")
                || blob.contains("no such");
    }

    static String parseDigest(String output) {
        if (output == null) {
            return "";
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (!trimmed.substring(0, colon).trim().equalsIgnoreCase("digest")) {
                continue;
            }
            String value = trimmed.substring(colon + 1).trim();
            if (value.startsWith("sha256:") && value.length() > "sha256:".length()) {
                return value;
            }
        }
        return "";
    }

    private static BizException pushFailed(String password, HelmProcessResult result) {
        String detail = redact(firstLine(result), password);
        if (detail.isBlank()) {
            detail = "helm 退出码 " + result.exitCode();
        }
        return new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "推送到 OCI 仓库失败: " + detail);
    }

    private static String firstLine(HelmProcessResult result) {
        String text = result.stderr() == null || result.stderr().isBlank() ? result.stdout() : result.stderr();
        if (text == null) {
            return "";
        }
        int nl = text.indexOf('\n');
        String line = (nl < 0 ? text : text.substring(0, nl)).trim();
        return line.length() > 300 ? line.substring(0, 300) : line;
    }

    static String redact(String text, String secret) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (secret == null || secret.isBlank()) {
            return text;
        }
        return text.replace(secret, "******");
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup of helm config
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
