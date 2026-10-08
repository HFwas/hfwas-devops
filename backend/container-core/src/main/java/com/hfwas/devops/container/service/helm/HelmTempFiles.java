package com.hfwas.devops.container.service.helm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Private temp directories for helm. Callers delete them when the command finishes.
 */
final class HelmTempFiles {

    private HelmTempFiles() {
    }

    static Path createPrivateDir(String prefix) throws IOException {
        Path dir = Files.createTempDirectory(prefix);
        ownerOnly(dir, true);
        return dir;
    }

    static void ownerOnly(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> perms = directory
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some container filesystems have no POSIX bits. The directory is still temporary.
        }
    }

    static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort so kubeconfig and registry config do not linger
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
