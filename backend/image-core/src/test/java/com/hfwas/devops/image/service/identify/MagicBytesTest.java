package com.hfwas.devops.image.service.identify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MagicBytesTest {

    @Test
    void detectsJpegFromSoiMarker() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertEquals("image/jpeg", MagicBytes.detectMime(jpeg));
    }

    @Test
    void detectsPngSignature() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertEquals("image/png", MagicBytes.detectMime(png));
    }

    @Test
    void detectsGif() {
        assertEquals("image/gif", MagicBytes.detectMime("GIF89a".getBytes()));
    }

    @Test
    void detectsWebpRiff() {
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);
        assertEquals("image/webp", MagicBytes.detectMime(webp));
    }

    @Test
    void detectsBmp() {
        assertEquals("image/bmp", MagicBytes.detectMime("BM".getBytes()));
    }

    @Test
    void detectsTiffLittleEndian() {
        byte[] tiff = {0x49, 0x49, 0x2A, 0x00};
        assertEquals("image/tiff", MagicBytes.detectMime(tiff));
    }

    @Test
    void detectsHeicFtypBrand() {
        byte[] heic = new byte[20];
        System.arraycopy("ftyp".getBytes(), 0, heic, 4, 4);
        System.arraycopy("heic".getBytes(), 0, heic, 8, 4);
        assertEquals("image/heic", MagicBytes.detectMime(heic));
    }

    @Test
    void returnsNullForUnknownBytes() {
        assertNull(MagicBytes.detectMime("not-an-image".getBytes()));
        assertNull(MagicBytes.detectMime(new byte[0]));
        assertNull(MagicBytes.detectMime(null));
    }
}
