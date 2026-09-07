package com.hfwas.devops.image;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestImages {

    private TestImages() {
    }

    public static byte[] jpeg(int width, int height) throws IOException {
        return encode("jpeg", rgb(width, height, false));
    }

    public static byte[] png(int width, int height, boolean alpha) throws IOException {
        return encode("png", rgb(width, height, alpha));
    }

    public static Path writeJpeg(Path dir, String name, int width, int height) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, jpeg(width, height));
        return file;
    }

    public static Path writePng(Path dir, String name, int width, int height, boolean alpha) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, png(width, height, alpha));
        return file;
    }

    private static BufferedImage rgb(int width, int height, boolean alpha) {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(32, 128, 240, alpha ? 128 : 255));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillRect(4, 4, Math.max(1, width / 3), Math.max(1, height / 3));
        g.dispose();
        return image;
    }

    private static byte[] encode(String format, BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IOException("no writer for " + format);
        }
        return out.toByteArray();
    }
}
