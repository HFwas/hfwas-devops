package com.hfwas.devops.container.service.helm;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ChartArchiveFixtures {

    private ChartArchiveFixtures() {
    }

    static String chartYaml(String name, String version) {
        return """
                apiVersion: v2
                name: %s
                description: Tiny fixture chart
                type: application
                version: %s
                appVersion: 1.10
                """.formatted(name, version);
    }

    static byte[] tgz(Entry... entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Entry entry : entries) {
                entry.write(tar);
            }
            tar.finish();
        }
        return bos.toByteArray();
    }

    static Path write(Path dir, String filename, byte[] bytes) throws IOException {
        Files.createDirectories(dir);
        Path path = dir.resolve(filename);
        Files.write(path, bytes);
        return path;
    }

    static Entry file(String name, String text) {
        return new FileEntry(name, text.getBytes(StandardCharsets.UTF_8));
    }

    static Entry file(String name, byte[] bytes) {
        return new FileEntry(name, bytes);
    }

    static Entry symlink(String name, String target) {
        return new SymlinkEntry(name, target);
    }

    sealed interface Entry permits FileEntry, SymlinkEntry {
        void write(TarArchiveOutputStream tar) throws IOException;
    }

    private record FileEntry(String name, byte[] data) implements Entry {
        @Override
        public void write(TarArchiveOutputStream tar) throws IOException {
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(data.length);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
        }
    }

    private record SymlinkEntry(String name, String target) implements Entry {
        @Override
        public void write(TarArchiveOutputStream tar) throws IOException {
            TarArchiveEntry entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
            entry.setLinkName(target);
            entry.setSize(0);
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
        }
    }

    static byte[] validChart(String name, String version) throws IOException {
        List<Entry> entries = new ArrayList<>();
        entries.add(file(name + "/Chart.yaml", chartYaml(name, version)));
        entries.add(file(name + "/values.yaml", "replicaCount: 1\n"));
        entries.add(file(name + "/templates/NOTES.txt", "hello\n"));
        return tgz(entries.toArray(Entry[]::new));
    }
}
