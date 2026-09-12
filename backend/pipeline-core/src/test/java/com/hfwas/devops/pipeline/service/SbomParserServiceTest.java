package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.mapper.DependencyComponentMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SbomParserServiceTest {

    @Mock
    private DependencyComponentMapper componentMapper;
    @Mock
    private PipelineRunMapper runMapper;

    @Captor
    private ArgumentCaptor<DependencyComponentEntity> captor;

    private SbomParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new SbomParserService(componentMapper, runMapper);
        PipelineRunEntity run = new PipelineRunEntity();
        run.setPipelineId(9L);
        lenient().when(runMapper.selectById(any())).thenReturn(run);
        lenient().when(componentMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void parseValidCycloneDxJson() {
        String sbom = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "components": [
                    {
                      "type": "library",
                      "group": "org.springframework.boot",
                      "name": "spring-boot-starter-web",
                      "version": "3.3.0",
                      "purl": "pkg:maven/org.springframework.boot/spring-boot-starter-web@3.3.0",
                      "licenses": [{"license": {"id": "Apache-2.0"}}],
                      "scope": "required"
                    },
                    {
                      "type": "library",
                      "group": "com.google.guava",
                      "name": "guava",
                      "version": "33.2.0-jre",
                      "purl": "pkg:maven/com.google.guava/guava@33.2.0-jre",
                      "licenses": [{"license": {"id": "Apache-2.0"}}],
                      "scope": "compile"
                    },
                    {
                      "type": "library",
                      "name": "lodash",
                      "version": "4.17.21",
                      "purl": "pkg:npm/lodash@4.17.21",
                      "licenses": [{"license": {"id": "MIT"}}],
                      "scope": "required"
                    }
                  ]
                }
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        int count = parserService.parse(100L, 200L, stream);
        assertEquals(3, count);
        verify(componentMapper, times(3)).insert(captor.capture());

        List<DependencyComponentEntity> entities = captor.getAllValues();
        DependencyComponentEntity first = entities.get(0);
        assertEquals(100L, first.getArtifactId());
        assertEquals(200L, first.getRunId());
        assertEquals(9L, first.getPipelineId());
        assertEquals("pkg:maven/org.springframework.boot/spring-boot-starter-web@3.3.0", first.getPurl());
        assertEquals("pkg:maven/org.springframework.boot/spring-boot-starter-web@3.3.0", first.getIdentityKey());
        assertEquals("org.springframework.boot", first.getGroupName());
        assertEquals("spring-boot-starter-web", first.getName());
        assertEquals("3.3.0", first.getVersion());
        assertEquals("Apache-2.0", first.getLicense());
        assertEquals("required", first.getScope());
        assertEquals("java", first.getLanguage());

        DependencyComponentEntity third = entities.get(2);
        assertEquals("lodash", third.getName());
        assertEquals("javascript", third.getLanguage());
        assertEquals("MIT", third.getLicense());
    }

    @Test
    void parseEmptyComponentsReturnsZero() {
        String sbom = """
                {"bomFormat": "CycloneDX", "specVersion": "1.6", "components": []}
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        int count = parserService.parse(101L, 201L, stream);
        assertEquals(0, count);
        verify(componentMapper, never()).insert(any(DependencyComponentEntity.class));
    }

    @Test
    void parseNoComponentsReturnsZero() {
        String sbom = """
                {"bomFormat": "CycloneDX", "specVersion": "1.6"}
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        int count = parserService.parse(102L, 202L, stream);
        assertEquals(0, count);
        verify(componentMapper, never()).insert(any(DependencyComponentEntity.class));
    }

    @Test
    void parseComponentWithoutPurlDetectsLanguageFromGroup() {
        String sbom = """
                {
                  "bomFormat": "CycloneDX",
                  "components": [
                    {
                      "type": "library",
                      "group": "org.apache.commons",
                      "name": "commons-lang3",
                      "version": "3.14.0"
                    }
                  ]
                }
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        parserService.parse(103L, 203L, stream);
        verify(componentMapper).insert(captor.capture());
        DependencyComponentEntity entity = captor.getValue();
        assertEquals("java", entity.getLanguage(), "group 含 org. 时按 Java 推断");
        assertEquals("", entity.getPurl());
        assertEquals("gav:org.apache.commons|commons-lang3|3.14.0", entity.getIdentityKey());
        assertNull(entity.getLicense());
        assertNull(entity.getScope());
    }

    @Test
    void parseDetectsMultipleLanguages() {
        String sbom = """
                {
                  "bomFormat": "CycloneDX",
                  "components": [
                    {"type": "library", "name": "express", "version": "4.19.2", "purl": "pkg:npm/express@4.19.2"},
                    {"type": "library", "name": "glog", "version": "1.2.0", "purl": "pkg:golang/github.com/golang/glog@1.2.0"},
                    {"type": "library", "name": "requests", "version": "2.31.0", "purl": "pkg:pypi/requests@2.31.0"}
                  ]
                }
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        parserService.parse(104L, 204L, stream);
        verify(componentMapper, times(3)).insert(captor.capture());
        List<DependencyComponentEntity> entities = captor.getAllValues();
        assertEquals("javascript", entities.get(0).getLanguage());
        assertEquals("go", entities.get(1).getLanguage());
        assertEquals("python", entities.get(2).getLanguage());
    }

    @Test
    void parseDedupesSamePurlInsideOneSbom() {
        String sbom = """
                {
                  "bomFormat": "CycloneDX",
                  "components": [
                    {"type": "library", "name": "jsqlparser", "version": "5.1", "purl": "pkg:maven/com.github.jsqlparser/jsqlparser@5.1?type=jar"},
                    {"type": "library", "name": "jsqlparser", "version": "5.1", "purl": "pkg:maven/com.github.jsqlparser/jsqlparser@5.1?type=jar"}
                  ]
                }
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        int count = parserService.parse(105L, 205L, stream);
        assertEquals(1, count);
        verify(componentMapper, times(1)).insert(any(DependencyComponentEntity.class));
        verify(componentMapper, never()).updateById(any(DependencyComponentEntity.class));
    }

    @Test
    void parseUpdatesExistingRowForSamePipelineAndPurl() {
        DependencyComponentEntity existing = new DependencyComponentEntity();
        existing.setId(77L);
        existing.setPipelineId(9L);
        existing.setIdentityKey("pkg:maven/com.github.jsqlparser/jsqlparser@5.1?type=jar");
        when(componentMapper.selectOne(any())).thenReturn(existing);

        String sbom = """
                {
                  "bomFormat": "CycloneDX",
                  "components": [
                    {
                      "type": "library",
                      "group": "com.github.jsqlparser",
                      "name": "jsqlparser",
                      "version": "5.1",
                      "purl": "pkg:maven/com.github.jsqlparser/jsqlparser@5.1?type=jar",
                      "licenses": [{"license": {"id": "LGPL-2.1-only"}}]
                    }
                  ]
                }
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        int count = parserService.parse(106L, 206L, stream);
        assertEquals(1, count);
        verify(componentMapper, never()).insert(any(DependencyComponentEntity.class));
        verify(componentMapper).updateById(captor.capture());
        DependencyComponentEntity updated = captor.getValue();
        assertEquals(77L, updated.getId());
        assertEquals(106L, updated.getArtifactId());
        assertEquals(206L, updated.getRunId());
        assertEquals(9L, updated.getPipelineId());
        assertEquals("LGPL-2.1-only", updated.getLicense());
    }
}
