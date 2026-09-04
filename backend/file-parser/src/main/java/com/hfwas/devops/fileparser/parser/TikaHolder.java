package com.hfwas.devops.fileparser.parser;

import org.apache.tika.Tika;
import org.apache.tika.parser.AutoDetectParser;

/**
 * Tika 4 的 AutoDetectParser / Detector 初始化很重（加载全部 parser 模块），
 * 进程内复用同一实例，避免每个请求都重新加载。
 */
public final class TikaHolder {

    private static final AutoDetectParser PARSER = new AutoDetectParser();
    private static final Tika TIKA = new Tika(PARSER.getDetector(), PARSER);

    private TikaHolder() {
    }

    public static AutoDetectParser parser() {
        return PARSER;
    }

    public static Tika tika() {
        return TIKA;
    }
}
