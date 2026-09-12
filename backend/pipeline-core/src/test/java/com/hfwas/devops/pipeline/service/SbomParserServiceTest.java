package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import com.hfwas.devops.pipeline.mapper.DependencyComponentMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SbomParserServiceTest {

    @Mock
    private DependencyComponentMapper componentMapper;

    @Captor
    private ArgumentCaptor<DependencyComponentEntity> captor;

    private SbomParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new SbomParserService(componentMapper);
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
        // 验证第一个组件
        DependencyComponentEntity first = entities.get(0);
        assertEquals(100L, first.getArtifactId());
        assertEquals(200L, first.getRunId());
        assertEquals("pkg:maven/org.springframework.boot/spring-boot-starter-web@3.3.0", first.getPurl());
        assertEquals("org.springframework.boot", first.getGroupName());
        assertEquals("spring-boot-starter-web", first.getName());
        assertEquals("3.3.0", first.getVersion());
        assertEquals("Apache-2.0", first.getLicense());
        assertEquals("required", first.getScope());
        assertEquals("java", first.getLanguage());

        // 验证 Node 组件语言推断
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
    }

    @Test
    void parseNoComponentsReturnsZero() {
        String sbom = """
                {"bomFormat": "CycloneDX", "specVersion": "1.6"}
                """;
        InputStream stream = new ByteArrayInputStream(sbom.getBytes(StandardCharsets.UTF_8));
        int count = parserService.parse(102L, 202L, stream);
        assertEquals(0, count);
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
        assertEquals("unknown", entity.getLanguage(), "without purl, should fallback to unknown");
        assertNull(entity.getPurl(), "purl should be empty string, not null");
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
}