package com.hfwas.devops.fileparser.ocr;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OcrPythonWorkerTest {

    @Test
    void shouldRecognizeViaStubWorker() throws Exception {
        URL stub = getClass().getResource("/ocr/ocr_worker_stub.py");
        assertNotNull(stub, "stub worker missing from test resources");
        Path script = Path.of(stub.toURI());

        try (OcrPythonWorker worker = new OcrPythonWorker("python3", script.toString(), 5_000)) {
            assertTrue(worker.isAvailable());
            File image = new File("/tmp/demo.png");
            OcrPythonWorker.Result result = worker.recognize(image);
            assertTrue(result.ok());
            assertEquals("stub:" + image.getAbsolutePath(), result.text());
            assertEquals(0.99, result.confidence(), 0.001);
        }
    }

    @Test
    void shouldFailWhenLocalModelsMissing() throws Exception {
        URL worker = getClass().getResource("/ocr/ocr_worker.py");
        assertNotNull(worker, "ocr_worker.py missing from classpath");
        Path script = Path.of(worker.toURI());

        IOException ex = assertThrows(IOException.class, () ->
                new OcrPythonWorker("python3", script.toString(), 5_000, Map.of(
                        "FILE_PARSER_OCR_DET_MODEL_DIR", "/tmp/hfwas-missing-ocr-det",
                        "FILE_PARSER_OCR_REC_MODEL_DIR", "/tmp/hfwas-missing-ocr-rec"
                )));
        assertTrue(ex.getMessage().contains("本地 OCR 模型不存在"), ex.getMessage());
    }

    @Test
    void shouldRejectAfterClose() throws Exception {
        URL stub = getClass().getResource("/ocr/ocr_worker_stub.py");
        Path script = Path.of(stub.toURI());
        OcrPythonWorker worker = new OcrPythonWorker("python3", script.toString(), 5_000);
        worker.close();
        assertFalse(worker.isAvailable());
        assertThrows(IllegalStateException.class, () -> worker.recognize(new File("a.png")));
    }
}
