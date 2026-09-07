package com.hfwas.devops.image.service;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crockford Base32 ULID（26 字符），会话 id 不可枚举。
 */
public final class Ulids {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger COUNTER = new AtomicInteger(RANDOM.nextInt());

    private Ulids() {
    }

    public static String next() {
        byte[] bytes = new byte[16];
        long ts = System.currentTimeMillis();
        bytes[0] = (byte) (ts >>> 40);
        bytes[1] = (byte) (ts >>> 32);
        bytes[2] = (byte) (ts >>> 24);
        bytes[3] = (byte) (ts >>> 16);
        bytes[4] = (byte) (ts >>> 8);
        bytes[5] = (byte) ts;
        RANDOM.nextBytes(bytes);
        // restore timestamp after random fill of remaining; rewrite first 6
        bytes[0] = (byte) (ts >>> 40);
        bytes[1] = (byte) (ts >>> 32);
        bytes[2] = (byte) (ts >>> 24);
        bytes[3] = (byte) (ts >>> 16);
        bytes[4] = (byte) (ts >>> 8);
        bytes[5] = (byte) ts;
        int c = COUNTER.incrementAndGet();
        bytes[15] ^= (byte) c;
        return encode(bytes);
    }

    private static String encode(byte[] bytes) {
        char[] out = new char[26];
        long hi = 0;
        for (int i = 0; i < 5; i++) {
            hi = (hi << 8) | (bytes[i] & 0xFFL);
        }
        // 40 bits from first 5 bytes → wait, ULID is 48-bit time + 80-bit random = 128 bits → 26 * 5 = 130 bits with 2 pad
        int value = 0;
        int bits = 0;
        int index = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out[index++] = ALPHABET[(value >>> (bits - 5)) & 31];
                bits -= 5;
            }
        }
        if (bits > 0 && index < 26) {
            out[index++] = ALPHABET[(value << (5 - bits)) & 31];
        }
        while (index < 26) {
            out[index++] = '0';
        }
        return new String(out);
    }
}
