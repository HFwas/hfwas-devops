package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.pipeline.dto.DependencyComponentAggregateVO;
import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.mapper.DependencyComponentMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyComponentServiceTest {

    @Mock
    private DependencyComponentMapper componentMapper;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private PipelineMapper pipelineMapper;

    private DependencyComponentService componentService;

    @BeforeEach
    void setUp() {
        componentService = new DependencyComponentService(componentMapper, runMapper, pipelineMapper);
    }

    @Test
    void pageAggregatedGroupsSameComponentAcrossRuns() {
        DependencyComponentEntity comp1 = component("pkg:maven/log4j/log4j@2.20.0", "log4j", "2.20.0", "java", 1L);
        DependencyComponentEntity comp2 = component("pkg:maven/log4j/log4j@2.20.0", "log4j", "2.20.0", "java", 2L);
        DependencyComponentEntity comp3 = component("pkg:npm/express@4.19.2", "express", "4.19.2", "javascript", 3L);

        when(componentMapper.selectList(any())).thenReturn(List.of(comp1, comp2, comp3));

        // 模拟 run/pipeline 查询
        PipelineRunEntity run1 = new PipelineRunEntity();
        run1.setPipelineId(10L);
        when(runMapper.selectById(1L)).thenReturn(run1);
        PipelineRunEntity run2 = new PipelineRunEntity();
        run2.setPipelineId(10L);
        when(runMapper.selectById(2L)).thenReturn(run2);
        PipelineRunEntity run3 = new PipelineRunEntity();
        run3.setPipelineId(20L);
        when(runMapper.selectById(3L)).thenReturn(run3);

        PipelineEntity pipeline1 = new PipelineEntity();
        pipeline1.setName("app-backend");
        pipeline1.setRepoUrl("https://github.com/org/backend.git");
        when(pipelineMapper.selectById(10L)).thenReturn(pipeline1);
        PipelineEntity pipeline2 = new PipelineEntity();
        pipeline2.setName("app-frontend");
        when(pipelineMapper.selectById(20L)).thenReturn(pipeline2);

        IPage<DependencyComponentAggregateVO> result = componentService.pageAggregated(1, 20, null, null);

        assertNotNull(result);
        assertEquals(2, result.getRecords().size(), "should merge log4j entries into 1");

        DependencyComponentAggregateVO log4jAgg = result.getRecords().stream()
                .filter(a -> "log4j".equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(log4jAgg);
        assertEquals(2, log4jAgg.getOccurrenceCount(), "log4j appears in 2 runs");
        assertEquals("java", log4jAgg.getLanguage());

        DependencyComponentAggregateVO expressAgg = result.getRecords().stream()
                .filter(a -> "express".equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(expressAgg);
        assertEquals(1, expressAgg.getOccurrenceCount(), "express appears in 1 run");
        assertEquals("javascript", expressAgg.getLanguage());
    }

    @Test
    void pageAggregatedFiltersBySearch() {
        DependencyComponentEntity comp1 = component("pkg:maven/log4j/log4j@2.20.0", "log4j", "2.20.0", "java", 1L);
        DependencyComponentEntity comp2 = component("pkg:maven/guava/guava@33.2.0", "guava", "33.2.0", "java", 1L);

        when(componentMapper.selectList(any())).thenReturn(List.of(comp1, comp2));

        IPage<DependencyComponentAggregateVO> result = componentService.pageAggregated(1, 20, "log4j", null);
        assertEquals(1, result.getRecords().size(), "should filter to only log4j");
        assertEquals("log4j", result.getRecords().getFirst().getName());
    }

    @Test
    void pageAggregatedFiltersByLanguage() {
        DependencyComponentEntity comp1 = component("pkg:maven/log4j/log4j@2.20.0", "log4j", "2.20.0", "java", 1L);
        DependencyComponentEntity comp2 = component("pkg:npm/express@4.19.2", "express", "4.19.2", "javascript", 2L);

        when(componentMapper.selectList(any())).thenReturn(List.of(comp1, comp2));

        IPage<DependencyComponentAggregateVO> result = componentService.pageAggregated(1, 20, null, "java");
        assertEquals(1, result.getRecords().size());
        assertEquals("log4j", result.getRecords().getFirst().getName());
    }

    @Test
    void pageAggregatedSortsByOccurrenceDesc() {
        DependencyComponentEntity comp1 = component("pkg:a/1@1", "a", "1", "java", 1L);
        DependencyComponentEntity comp2 = component("pkg:b/2@1", "b", "1", "java", 1L);
        DependencyComponentEntity comp3 = component("pkg:a/1@1", "a", "1", "java", 2L);
        DependencyComponentEntity comp4 = component("pkg:a/1@1", "a", "1", "java", 3L);

        when(componentMapper.selectList(any())).thenReturn(List.of(comp1, comp2, comp3, comp4));
        when(runMapper.selectById(any())).thenReturn(new PipelineRunEntity());
        when(pipelineMapper.selectById(any())).thenReturn(new PipelineEntity());

        IPage<DependencyComponentAggregateVO> result = componentService.pageAggregated(1, 20, null, null);
        assertEquals(2, result.getRecords().size());
        assertEquals("a", result.getRecords().get(0).getName(), "most frequent should be first");
        assertEquals(3, result.getRecords().get(0).getOccurrenceCount());
        assertEquals("b", result.getRecords().get(1).getName());
        assertEquals(1, result.getRecords().get(1).getOccurrenceCount());
    }

    private static DependencyComponentEntity component(String purl, String name, String version, String language, Long runId) {
        DependencyComponentEntity entity = new DependencyComponentEntity();
        entity.setPurl(purl);
        entity.setGroupName(null);
        entity.setName(name);
        entity.setVersion(version);
        entity.setLanguage(language);
        entity.setRunId(runId);
        entity.setArtifactId(runId * 10);
        return entity;
    }
}