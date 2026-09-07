package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.NativeProcessRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageMagickTransformerTest {

    @TempDir
    Path tempDir;

    @Mock
    private EngineProbe probe;
    @Mock
    private NativeProcessRunner runner;

    private ImageMagickTransformer transformer;

    @BeforeEach
    void setUp() {
        ImageProcessorConfig config = new ImageProcessorConfig();
        transformer = new ImageMagickTransformer(config, probe, runner);
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertUsesUnlockedIdentifyToAvoidLimiterReentry() throws Exception {
        Path src = tempDir.resolve("in.jpg");
        Path dest = tempDir.resolve("out.png");
        Files.writeString(src, "x");
        when(runner.run(any(), any(), any(Duration.class))).thenReturn("");
        when(runner.runUnlocked(any(), any(), any(Duration.class))).thenReturn("10 8");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("png");
        ImageMagickTransformer.Result result = transformer.convert(src, dest, "png", request);
        assertEquals(10, result.getWidth());
        assertEquals(8, result.getHeight());
        ArgumentCaptor<List<String>> runCmd = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> unlockedCmd = ArgumentCaptor.forClass(List.class);
        verify(runner).run(runCmd.capture(), eq(src.getParent()), any(Duration.class));
        verify(runner).runUnlocked(unlockedCmd.capture(), eq(dest.getParent()), any(Duration.class));
        assertFalse(runCmd.getValue().contains("identify"));
        assertTrue(unlockedCmd.getValue().contains("identify"));
    }
}
