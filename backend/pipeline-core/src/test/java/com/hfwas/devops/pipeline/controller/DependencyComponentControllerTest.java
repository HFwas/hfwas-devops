package com.hfwas.devops.pipeline.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pipeline.dto.DependencyComponentAggregateVO;
import com.hfwas.devops.pipeline.dto.DependencyComponentVO;
import com.hfwas.devops.pipeline.service.DependencyComponentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-121、122。
 */
@ExtendWith(MockitoExtension.class)
class DependencyComponentControllerTest {

    @Mock
    private DependencyComponentService componentService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DependencyComponentController(componentService))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-121: 组件分页与过滤参数")
    void pagePassesFilters() throws Exception {
        Page<DependencyComponentVO> page = new Page<>(1, 20);
        DependencyComponentVO vo = new DependencyComponentVO();
        vo.setName("jackson");
        vo.setLanguage("JAVA");
        page.setRecords(List.of(vo));
        when(componentService.pageComponents(eq(1), eq(20), eq("jackson"), eq("JAVA"))).thenReturn(page);

        mvc.perform(get("/dependency/components/page")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .param("search", "jackson")
                        .param("language", "JAVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("jackson"));
    }

    @Test
    @DisplayName("PIPE-122: 组件聚合")
    void aggregatedReturnsRows() throws Exception {
        Page<DependencyComponentAggregateVO> page = new Page<>(1, 20);
        DependencyComponentAggregateVO vo = new DependencyComponentAggregateVO();
        vo.setName("jackson");
        vo.setOccurrenceCount(3);
        page.setRecords(List.of(vo));
        when(componentService.pageAggregated(eq(1), eq(20), eq(null), eq(null))).thenReturn(page);

        mvc.perform(get("/dependency/components/aggregated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].occurrenceCount").value(3));
    }
}
