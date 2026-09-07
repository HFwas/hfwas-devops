package com.hfwas.devops.image.controller;

import com.hfwas.devops.image.dto.ImageHealthVO;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.ImageSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImageProcessorControllerTest {

    @Mock
    private ImageSessionService sessionService;
    @Mock
    private EngineProbe engineProbe;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ImageProcessorController(sessionService, engineProbe)).build();
    }

    @Test
    void healthReturnsEngineFlags() throws Exception {
        when(engineProbe.health()).thenReturn(ImageHealthVO.builder()
                .magick(true)
                .exiftool(false)
                .heicDelegate(true)
                .build());
        mvc.perform(get("/api/image/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.magick").value(true))
                .andExpect(jsonPath("$.data.exiftool").value(false))
                .andExpect(jsonPath("$.data.heicDelegate").value(true));
    }
}
