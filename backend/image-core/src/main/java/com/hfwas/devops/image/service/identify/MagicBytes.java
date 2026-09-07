package com.hfwas.devops.image.service.identify;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 用文件头魔数判断图片类型，不信任扩展名或 Content-Type。
 */
public final class MagicBytes {

    private static final Set<String> HEIC_BRANDS = Set.of(
            "heic", "heif", "heix", "heim", "heis", "mif1", "msf1");

    private MagicBytes() {
    }

    public static String detectMime(byte[] header) {
        if (header == null || header.length < 2) {
            return null;
        }
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return "image/gif";
        }
        if (startsWith(header, "BM".getBytes(StandardCharsets.US_ASCII))) {
            return "image/bmp";
        }
        if (startsWith(header, new byte[]{0x49, 0x49, 0x2A, 0x00})
                || startsWith(header, new byte[]{0x4D, 0x4D, 0x00, 0x2A})) {
            return "image/tiff";
        }
        if (header.length >= 12
                && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && regionEquals(header, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        if (header.length >= 12 && regionEquals(header, 4, "ftyp".getBytes(StandardCharsets.US_ASCII))) {
            String brand = ascii(header, 8, 4).toLowerCase(Locale.ROOT);
            if (HEIC_BRANDS.contains(brand)) {
                return "image/heic";
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return regionEquals(data, 0, prefix);
    }

    private static boolean regionEquals(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] data, int offset, int len) {
        if (data.length < offset + len) {
            return "";
        }
        return new String(data, offset, len, StandardCharsets.US_ASCII);
    }
}
