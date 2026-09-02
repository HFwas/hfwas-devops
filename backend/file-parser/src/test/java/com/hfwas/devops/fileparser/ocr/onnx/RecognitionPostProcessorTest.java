package com.hfwas.devops.fileparser.ocr.onnx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecognitionPostProcessorTest {

    @Test
    void shouldKeepWhitespaceCharactersAndPrefixBlank(@TempDir Path tmp) throws Exception {
        Path dict = tmp.resolve("dict.txt");
        Files.writeString(dict, "!\n\"\n#\n$\n%\n&\n'\n \n\u3000\n容\n器\n", StandardCharsets.UTF_8);

        List<String> chars = RecognitionPostProcessor.loadDict(dict);

        assertEquals("", chars.get(0));
        assertEquals("!", chars.get(1));
        assertEquals("'", chars.get(7));
        assertEquals(" ", chars.get(8));
        assertEquals("\u3000", chars.get(9));
        assertEquals("容", chars.get(10));
        assertEquals("器", chars.get(11));
        assertEquals(12, chars.size());
    }

    @Test
    void shouldDecodeUsingBlankAtIndexZero(@TempDir Path tmp) throws Exception {
        Path dict = tmp.resolve("dict.txt");
        Files.writeString(dict, "A\nB\nC\n", StandardCharsets.UTF_8);
        RecognitionPostProcessor decoder = new RecognitionPostProcessor(dict);

        float[][][] logits = new float[1][5][5];
        logits[0][0][0] = 9; // blank
        logits[0][1][1] = 9; // A
        logits[0][2][1] = 9; // A duplicate
        logits[0][3][2] = 9; // B
        logits[0][4][0] = 9; // blank

        assertEquals("AB", decoder.decode(logits));
    }

    @Test
    void shouldRejectCorruptedDictMissingIdeographicSpace(@TempDir Path tmp) throws Exception {
        Path dict = tmp.resolve("dict.txt");
        Files.writeString(dict, "!\n\"\n#\n$\n%\n&\n''\n(\n)\n", StandardCharsets.UTF_8);

        assertFalse(PaddleOcrEngine.isValidDict(dict, 9));
    }

    @Test
    void shouldInstallOfficialDictIntoModelDir(@TempDir Path tmp) throws Exception {
        Path installed = PaddleOcrEngine.ensureCharacterDict(tmp, "medium");
        assertTrue(PaddleOcrEngine.isValidDict(installed, 18708));

        List<String> chars = RecognitionPostProcessor.loadDict(installed);
        assertEquals("'", chars.get(7));
        assertTrue(chars.contains("\u3000"));
        assertTrue(chars.contains("容"));
        assertEquals(" ", chars.get(chars.size() - 1));
        assertEquals(18710, chars.size());
    }
}
