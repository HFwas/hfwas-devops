package com.hfwas.devops.fileparser.ocr;

/**
 * JavaCPP 的 {@code maxPhysicalBytes} 是 static final，只在 Pointer 类加载时读取系统属性。
 * 必须在加载 OpenCV / {@code OcrPipeline} 之前调用。
 */
public final class OcrNativeMemory {

    static final String LIMIT = "6G";

    private OcrNativeMemory() {
    }

    public static void configure() {
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", LIMIT);
        System.setProperty("org.bytedeco.javacpp.maxPhysicalBytes", LIMIT);
    }
}
