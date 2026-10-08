package com.hfwas.devops.container.service.helm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * argv for helm release operations. Passwords are never placed here; registry login uses stdin.
 */
final class HelmReleaseCommands {

    private HelmReleaseCommands() {
    }

    static List<String> login(String binary, String host, String username, boolean plainHttp) {
        List<String> argv = helm(binary);
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

    static List<String> list(String binary, Path kubeconfig, String namespace) {
        List<String> argv = helm(binary);
        argv.add("list");
        argv.add("--all");
        if (namespace == null || namespace.isBlank()) {
            argv.add("--all-namespaces");
        } else {
            argv.add("--namespace");
            argv.add(namespace);
        }
        outputJson(argv);
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> status(String binary, Path kubeconfig, String namespace, String name) {
        List<String> argv = helm(binary);
        argv.add("status");
        argv.add(name);
        namespace(argv, namespace);
        outputJson(argv);
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> values(String binary, Path kubeconfig, String namespace, String name, Integer revision) {
        List<String> argv = helm(binary);
        argv.add("get");
        argv.add("values");
        argv.add(name);
        namespace(argv, namespace);
        argv.add("--output");
        argv.add("yaml");
        if (revision != null) {
            argv.add("--revision");
            argv.add(Integer.toString(revision));
        }
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> history(String binary, Path kubeconfig, String namespace, String name) {
        List<String> argv = helm(binary);
        argv.add("history");
        argv.add(name);
        namespace(argv, namespace);
        outputJson(argv);
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> manifest(String binary, Path kubeconfig, String namespace, String name) {
        List<String> argv = helm(binary);
        argv.add("get");
        argv.add("manifest");
        argv.add(name);
        namespace(argv, namespace);
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> install(
            String binary,
            Path kubeconfig,
            HelmChartRef chart,
            String releaseName,
            String namespace,
            Path valuesFile,
            boolean createNamespace,
            boolean wait,
            boolean dryRun,
            boolean plainHttp,
            String timeout) {
        List<String> argv = helm(binary);
        argv.add("install");
        argv.add(releaseName);
        chart(argv, chart, valuesFile, plainHttp);
        namespace(argv, namespace);
        if (createNamespace) {
            argv.add("--create-namespace");
        }
        if (dryRun) {
            argv.add("--dry-run=client");
            outputJson(argv);
        } else if (wait) {
            wait(argv, timeout);
        }
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> upgrade(
            String binary,
            Path kubeconfig,
            HelmChartRef chart,
            String releaseName,
            String namespace,
            Path valuesFile,
            boolean wait,
            boolean atomic,
            boolean dryRun,
            boolean plainHttp,
            String timeout) {
        List<String> argv = helm(binary);
        argv.add("upgrade");
        argv.add(releaseName);
        chart(argv, chart, valuesFile, plainHttp);
        namespace(argv, namespace);
        if (dryRun) {
            argv.add("--dry-run=client");
            outputJson(argv);
        } else if (atomic) {
            argv.add("--atomic");
            argv.add("--timeout");
            argv.add(timeout);
        } else if (wait) {
            wait(argv, timeout);
        }
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> rollback(
            String binary,
            Path kubeconfig,
            String namespace,
            String name,
            int revision,
            boolean wait,
            String timeout) {
        List<String> argv = helm(binary);
        argv.add("rollback");
        argv.add(name);
        argv.add(Integer.toString(revision));
        namespace(argv, namespace);
        if (wait) {
            wait(argv, timeout);
        }
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    static List<String> uninstall(String binary, Path kubeconfig, String namespace, String name) {
        List<String> argv = helm(binary);
        argv.add("uninstall");
        argv.add(name);
        namespace(argv, namespace);
        kubeconfig(argv, kubeconfig);
        return List.copyOf(argv);
    }

    private static List<String> helm(String binary) {
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        return argv;
    }

    private static void chart(List<String> argv, HelmChartRef chart, Path valuesFile, boolean plainHttp) {
        argv.add(chart.locator());
        argv.add("--version");
        argv.add(chart.version());
        if (valuesFile != null) {
            argv.add("--values");
            argv.add(valuesFile.toString());
        }
        if (plainHttp) {
            argv.add("--plain-http");
        }
    }

    private static void namespace(List<String> argv, String namespace) {
        argv.add("--namespace");
        argv.add(namespace);
    }

    private static void outputJson(List<String> argv) {
        argv.add("--output");
        argv.add("json");
    }

    private static void kubeconfig(List<String> argv, Path kubeconfig) {
        argv.add("--kubeconfig");
        argv.add(kubeconfig.toString());
    }

    private static void wait(List<String> argv, String timeout) {
        argv.add("--wait");
        argv.add("--timeout");
        argv.add(timeout);
    }
}
