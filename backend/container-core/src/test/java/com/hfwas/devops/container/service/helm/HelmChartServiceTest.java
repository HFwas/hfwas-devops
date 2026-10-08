package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.HelmChartArtifactVO;
import com.hfwas.devops.container.dto.HelmChartSummaryVO;
import com.hfwas.devops.container.entity.HelmChartArtifactEntity;
import com.hfwas.devops.container.entity.HelmChartRepositoryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.error.HelmChartConflictException;
import com.hfwas.devops.container.mapper.HelmChartArtifactMapper;
import com.hfwas.devops.container.mapper.HelmChartRepositoryMapper;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.security.AuthUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

@ExtendWith(MockitoExtension.class)
class HelmChartServiceTest {

    @Mock
    private HelmChartRepositoryMapper repositoryMapper;
    @Mock
    private HelmChartArtifactMapper artifactMapper;
    @Mock
    private HelmChartOciPusher ociPusher;
    @Mock
    private HelmChartPackagePuller packagePuller;

    private HelmChartService service;
    private byte[] fixture;

    @BeforeEach
    void setUp() throws Exception {
        service = new HelmChartService(
                repositoryMapper,
                artifactMapper,
                ociPusher,
                packagePuller,
                new HelmChartArchiveInspector(),
                properties());
        try (InputStream in = getClass().getResourceAsStream("/helm/sample-0.1.0.tgz")) {
            fixture = in.readAllBytes();
        }
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadPersistsArtifactAndPushesToConfiguredRepo() {
        authenticate(42L, 9L);
        when(repositoryMapper.selectOne(any())).thenReturn(null);
        when(repositoryMapper.insert(any(HelmChartRepositoryEntity.class))).thenAnswer(invocation -> {
            HelmChartRepositoryEntity entity = invocation.getArgument(0);
            entity.setId(5L);
            return 1;
        });
        when(artifactMapper.selectCount(any())).thenReturn(0L);
        when(ociPusher.push(any())).thenAnswer(invocation -> {
            OciPushRequest request = invocation.getArgument(0);
            return new OciPushResult(request.reference().chartRef(request.chartName(), request.version()), "sha256:pushed");
        });
        when(artifactMapper.insert(any(HelmChartArtifactEntity.class))).thenAnswer(invocation -> {
            HelmChartArtifactEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });

        HelmChartArtifactVO vo = service.upload(chartFile("sample-0.1.0.tgz"), null);

        assertEquals(9L, vo.getId());
        assertEquals("sample", vo.getChartName());
        assertEquals("0.1.0", vo.getVersion());
        assertEquals("sha256:pushed", vo.getDigest());
        assertEquals("oci://harbor.example/charts/sample:0.1.0", vo.getChartRef());
        assertEquals("default", vo.getRepositoryName());
        assertEquals(42L, vo.getUploadedBy());
        assertTrue(vo.getSizeBytes() > 0);

        ArgumentCaptor<HelmChartRepositoryEntity> repoCaptor = ArgumentCaptor.forClass(HelmChartRepositoryEntity.class);
        verify(repositoryMapper).insert(repoCaptor.capture());
        assertEquals(9L, repoCaptor.getValue().getTenantId());
        ArgumentCaptor<HelmChartArtifactEntity> stored = ArgumentCaptor.forClass(HelmChartArtifactEntity.class);
        verify(artifactMapper).insert(stored.capture());
        assertEquals(Boolean.TRUE, stored.getValue().getContentCached());
        assertTrue(stored.getValue().getValuesYaml().contains("replicaCount: 1"));
        assertEquals("[]", stored.getValue().getKeywords());

        assertEquals("oci://harbor.example/charts", repoCaptor.getValue().getUrl());
        assertEquals("oci", repoCaptor.getValue().getType());

        ArgumentCaptor<OciPushRequest> pushCaptor = ArgumentCaptor.forClass(OciPushRequest.class);
        verify(ociPusher).push(pushCaptor.capture());
        assertEquals("sample", pushCaptor.getValue().chartName());
        assertEquals("0.1.0", pushCaptor.getValue().version());
        assertEquals("robot", pushCaptor.getValue().username());
        assertTrue(pushCaptor.getValue().toString().contains("sample:0.1.0"));
        assertTrue(!pushCaptor.getValue().toString().contains("s3cret"));
    }

    @Test
    void duplicateVersionDoesNotPush() {
        HelmChartRepositoryEntity repo = existingRepo();
        when(repositoryMapper.selectOne(any())).thenReturn(repo);
        when(artifactMapper.selectCount(any())).thenReturn(1L);

        HelmChartConflictException ex = assertThrows(HelmChartConflictException.class,
                () -> service.upload(chartFile("sample-0.1.0.tgz"), null));
        assertEquals(ContainerErrorCode.HELM_CHART_VERSION_EXISTS.getCode(), ex.getCode());
        verify(ociPusher, never()).push(any());
        verify(artifactMapper, never()).insert(any(HelmChartArtifactEntity.class));
    }

    @Test
    void remoteConflictDoesNotInsert() {
        when(repositoryMapper.selectOne(any())).thenReturn(existingRepo());
        when(artifactMapper.selectCount(any())).thenReturn(0L);
        when(ociPusher.push(any())).thenThrow(new HelmChartConflictException("OCI 仓库已存在该 Chart 版本，拒绝覆盖"));

        assertThrows(HelmChartConflictException.class, () -> service.upload(chartFile("sample-0.1.0.tgz"), null));
        verify(artifactMapper, never()).insert(any(HelmChartArtifactEntity.class));
    }

    @Test
    void pushFailureDoesNotInsert() {
        when(repositoryMapper.selectOne(any())).thenReturn(existingRepo());
        when(artifactMapper.selectCount(any())).thenReturn(0L);
        when(ociPusher.push(any())).thenThrow(new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "registry down"));

        BizException ex = assertThrows(BizException.class, () -> service.upload(chartFile("sample-0.1.0.tgz"), null));
        assertEquals(ContainerErrorCode.HELM_CHART_PUSH_FAILED.getCode(), ex.getCode());
        verify(artifactMapper, never()).insert(any(HelmChartArtifactEntity.class));
    }

    @Test
    void rejectsNonTgzBeforePush() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        BizException ex = assertThrows(BizException.class, () -> service.upload(file, null));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
        verify(ociPusher, never()).push(any());
        verify(repositoryMapper, never()).selectOne(any());
    }

    @Test
    void listsChartsByNewestSemver() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(
                artifact("sample", "1.0.0", LocalDateTime.parse("2026-01-01T00:00:00")),
                artifact("sample", "1.10.0", LocalDateTime.parse("2026-02-01T00:00:00")),
                artifact("demo", "0.1.0", LocalDateTime.parse("2026-03-01T00:00:00"))));

        List<HelmChartSummaryVO> charts = service.listCharts(null, null);
        assertEquals(2, charts.size());
        HelmChartSummaryVO sample = charts.stream().filter(item -> "sample".equals(item.getChartName())).findFirst().orElseThrow();
        assertEquals("1.10.0", sample.getLatestVersion());
        assertEquals(2, sample.getVersionCount());
    }

    @Test
    void getVersionReturnsSingleRow() {
        HelmChartArtifactEntity row = artifact("sample", "0.1.0", LocalDateTime.parse("2026-04-01T00:00:00"));
        row.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        when(artifactMapper.selectList(any())).thenReturn(List.of(row));

        HelmChartArtifactVO vo = service.getVersion("sample", "0.1.0", 5L);
        assertEquals("oci://harbor.example/charts/sample:0.1.0", vo.getChartRef());
    }

    @Test
    void listFiltersByChartNameDescriptionOrRepository() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(
                artifact("sample", "1.0.0", LocalDateTime.parse("2026-01-01T00:00:00")),
                artifact("demo", "0.1.0", LocalDateTime.parse("2026-03-01T00:00:00"))));
        when(repositoryMapper.selectList(any())).thenReturn(List.of(existingRepo()));

        List<HelmChartSummaryVO> charts = service.listCharts(null, "default");
        assertEquals(2, charts.size());

        charts = service.listCharts(null, "samp");
        assertEquals(1, charts.size());
        assertEquals("sample", charts.get(0).getChartName());
        assertEquals("default", charts.get(0).getRepositoryName());
    }

    @Test
    void getChartReturnsReadmeAndVersionsFromCache() {
        HelmChartArtifactEntity latest = artifact("sample", "1.2.0", LocalDateTime.parse("2026-05-01T00:00:00"));
        latest.setId(8L);
        latest.setContentCached(true);
        latest.setKeywords("[\"web\"]");
        latest.setReadme("# Sample\n");
        latest.setValuesYaml("replicaCount: 2\n");
        HelmChartArtifactEntity older = artifact("sample", "1.0.0", LocalDateTime.parse("2026-01-01T00:00:00"));
        older.setId(7L);
        older.setContentCached(true);
        when(artifactMapper.selectList(any())).thenReturn(List.of(older, latest));
        when(repositoryMapper.selectById(5L)).thenReturn(existingRepo());

        var detail = service.getChart("sample", 5L, null);

        assertEquals("sample", detail.getChartName());
        assertEquals("1.2.0", detail.getVersion());
        assertEquals("default", detail.getRepositoryName());
        assertEquals(List.of("web"), detail.getKeywords());
        assertEquals("# Sample\n", detail.getReadme());
        assertEquals(2, detail.getVersions().size());
        assertEquals("1.2.0", detail.getVersions().get(0).getVersion());
        verify(packagePuller, never()).pull(any());

        var values = service.getValues("sample", "1.2.0", 5L);
        assertEquals("replicaCount: 2\n", values.getValuesYaml());
    }

    @Test
    void pullsChartWhenContentIsNotCached() throws Exception {
        HelmChartArtifactEntity row = artifact("sample", "0.1.0", LocalDateTime.parse("2026-04-01T00:00:00"));
        row.setId(3L);
        row.setContentCached(false);
        row.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        when(artifactMapper.selectList(any())).thenReturn(List.of(row));
        when(repositoryMapper.selectById(5L)).thenReturn(existingRepo());
        when(packagePuller.pull(any())).thenAnswer(invocation -> {
            OciPullRequest request = invocation.getArgument(0);
            assertEquals("oci://harbor.example/charts/sample:0.1.0", request.chartRef());
            assertFalse(request.toString().contains("s3cret"));
            Path archive = request.destinationDir().resolve("sample-0.1.0.tgz");
            Files.write(archive, fixture);
            return archive;
        });

        var values = service.getValues("sample", "0.1.0", 5L);

        assertTrue(values.getValuesYaml().contains("replicaCount: 1"));
        assertEquals(Boolean.TRUE, row.getContentCached());
        verify(artifactMapper).updateById(row);
    }

    @Test
    void getMissingVersion() {
        when(artifactMapper.selectList(any())).thenReturn(List.of());
        BizException ex = assertThrows(BizException.class, () -> service.getVersion("sample", "9.9.9", null));
        assertEquals(ContainerErrorCode.HELM_CHART_NOT_FOUND.getCode(), ex.getCode());
    }

    private MockMultipartFile chartFile(String filename) {
        return new MockMultipartFile("file", filename, "application/gzip", fixture);
    }

    private static HelmChartRepositoryEntity existingRepo() {
        HelmChartRepositoryEntity repo = new HelmChartRepositoryEntity();
        repo.setId(5L);
        repo.setTenantId(HelmChartService.PLATFORM_TENANT_ID);
        repo.setName("default");
        repo.setType("oci");
        repo.setUrl("oci://harbor.example/charts");
        repo.setInsecure(false);
        return repo;
    }

    private static HelmChartArtifactEntity artifact(String name, String version, LocalDateTime createdAt) {
        HelmChartArtifactEntity entity = new HelmChartArtifactEntity();
        entity.setRepositoryId(5L);
        entity.setTenantId(0L);
        entity.setChartName(name);
        entity.setVersion(version);
        entity.setDescription(name + " desc");
        entity.setAppVersion("1");
        entity.setCreatedAt(createdAt);
        entity.setDigest("sha256:x");
        entity.setSizeBytes(10L);
        entity.setChartRef("oci://harbor.example/charts/" + name + ":" + version);
        return entity;
    }

    private static HelmChartProperties properties() {
        HelmChartProperties properties = new HelmChartProperties();
        properties.getOci().setUrl("oci://harbor.example/charts");
        properties.getOci().setUsername("robot");
        properties.getOci().setPassword("s3cret");
        properties.getOci().setRepositoryName("default");
        return properties;
    }

    private static void authenticate(long userId, long tenantId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("ada");
        user.setRole("user");
        user.setEnabled(1);
        AuthUserPrincipal principal = new AuthUserPrincipal(user, tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
