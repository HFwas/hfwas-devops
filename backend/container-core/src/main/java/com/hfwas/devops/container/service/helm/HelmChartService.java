package com.hfwas.devops.container.service.helm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.HelmChartArtifactVO;
import com.hfwas.devops.container.dto.HelmChartDetailVO;
import com.hfwas.devops.container.dto.HelmChartRepositoryVO;
import com.hfwas.devops.container.dto.HelmChartSummaryVO;
import com.hfwas.devops.container.dto.HelmChartVersionVO;
import com.hfwas.devops.container.dto.HelmValuesVO;
import com.hfwas.devops.container.entity.HelmChartArtifactEntity;
import com.hfwas.devops.container.entity.HelmChartRepositoryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.error.HelmChartConflictException;
import com.hfwas.devops.container.mapper.HelmChartArtifactMapper;
import com.hfwas.devops.container.mapper.HelmChartRepositoryMapper;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class HelmChartService {

    /** Used when the caller is authenticated but has no tenant on the principal. */
    static final long PLATFORM_TENANT_ID = 0L;

    private final HelmChartRepositoryMapper repositoryMapper;
    private final HelmChartArtifactMapper artifactMapper;
    private final HelmChartOciPusher ociPusher;
    private final HelmChartPackagePuller packagePuller;
    private final HelmChartArchiveInspector archiveInspector;
    private final HelmChartProperties properties;

    public HelmChartArtifactVO upload(MultipartFile file, Long repositoryId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "上传文件为空");
        }
        assertTgzFilename(file.getOriginalFilename());
        long maxBytes = properties.maxUploadBytes();
        if (file.getSize() > maxBytes) {
            throw new BizException(ContainerErrorCode.HELM_CHART_TOO_LARGE,
                    "文件超过 " + properties.getMaxUploadMb() + "MB 限制");
        }
        Path temp = null;
        try {
            temp = copyLimited(file, maxBytes);
            String fileDigest = sha256(temp);
            HelmChartPackageMeta meta = archiveInspector.inspect(temp, properties.archiveLimits());
            HelmChartRepositoryEntity repository = resolveRepository(repositoryId);
            assertAbsent(repository.getId(), meta.name(), meta.version());
            OciChartReference reference = OciChartReference.parse(repository.getUrl(),
                    Boolean.TRUE.equals(repository.getInsecure()) || properties.getOci().isInsecure());
            OciPushResult pushed = ociPusher.push(new OciPushRequest(
                    temp,
                    reference,
                    properties.getOci().getUsername(),
                    properties.getOci().getPassword(),
                    meta.name(),
                    meta.version()));
            String digest = pushed.digest() == null || pushed.digest().isBlank() ? fileDigest : pushed.digest();
            String chartRef = pushed.chartRef() == null || pushed.chartRef().isBlank()
                    ? reference.chartRef(meta.name(), meta.version())
                    : pushed.chartRef();
            HelmChartArtifactEntity entity = new HelmChartArtifactEntity();
            entity.setRepositoryId(repository.getId());
            entity.setTenantId(repository.getTenantId());
            entity.setChartName(meta.name());
            entity.setVersion(meta.version());
            entity.setDigest(digest);
            entity.setSizeBytes(Files.size(temp));
            entity.setChartRef(chartRef);
            entity.setDescription(meta.description() == null ? "" : meta.description());
            entity.setAppVersion(meta.appVersion() == null ? "" : meta.appVersion());
            HelmChartContent.apply(entity, meta);
            entity.setUploadedBy(SecurityHelper.currentUserId());
            entity.setCreatedAt(LocalDateTime.now());
            try {
                artifactMapper.insert(entity);
            } catch (DataIntegrityViolationException ex) {
                throw new HelmChartConflictException("相同 Chart 名称与版本已存在");
            }
            log.info("Stored helm chart artifact id={} ref={}", entity.getId(), chartRef);
            return toArtifactVO(entity, repository.getName());
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "读取上传文件失败");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    log.warn("Failed to delete temp chart {}", temp);
                }
            }
        }
    }

    public List<HelmChartSummaryVO> listCharts(Long repositoryId, String keyword) {
        List<HelmChartArtifactEntity> rows = artifactMapper.selectList(artifactFilter(repositoryId, null));
        Map<Long, String> repositoryNames = repositoryNames();
        Map<String, List<HelmChartArtifactEntity>> grouped = new LinkedHashMap<>();
        for (HelmChartArtifactEntity row : rows) {
            grouped.computeIfAbsent(row.getRepositoryId() + "\0" + row.getChartName(), key -> new ArrayList<>())
                    .add(row);
        }
        List<HelmChartSummaryVO> summaries = new ArrayList<>();
        for (List<HelmChartArtifactEntity> versions : grouped.values()) {
            versions.sort(Comparator.comparing(HelmChartArtifactEntity::getVersion, HelmVersions.NEWER_FIRST));
            HelmChartArtifactEntity latest = versions.get(0);
            HelmChartSummaryVO summary = new HelmChartSummaryVO();
            summary.setRepositoryId(latest.getRepositoryId());
            summary.setRepositoryName(repositoryNames.getOrDefault(latest.getRepositoryId(), ""));
            summary.setChartName(latest.getChartName());
            summary.setLatestVersion(latest.getVersion());
            summary.setVersionCount(versions.size());
            summary.setDescription(latest.getDescription());
            summary.setAppVersion(latest.getAppVersion());
            summary.setUpdatedAt(versions.stream()
                    .map(HelmChartArtifactEntity::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(latest.getCreatedAt()));
            summaries.add(summary);
        }
        if (keyword != null && !keyword.isBlank()) {
            String needle = keyword.trim().toLowerCase(Locale.ROOT);
            summaries.removeIf(summary -> !contains(summary.getChartName(), needle)
                    && !contains(summary.getDescription(), needle)
                    && !contains(summary.getRepositoryName(), needle));
        }
        summaries.sort(Comparator.comparing(HelmChartSummaryVO::getChartName, Comparator.nullsLast(String::compareTo))
                .thenComparing(HelmChartSummaryVO::getRepositoryId, Comparator.nullsLast(Long::compareTo)));
        return summaries;
    }

    public HelmChartDetailVO getChart(String chartName, Long repositoryId, String version) {
        List<HelmChartArtifactEntity> rows = loadVersions(chartName, repositoryId);
        HelmChartArtifactEntity selected = selectVersion(rows, version);
        HelmChartRepositoryEntity repository = requireRepository(selected.getRepositoryId());
        ensureContent(selected, repository);
        rows.sort(Comparator.comparing(HelmChartArtifactEntity::getVersion, HelmVersions.NEWER_FIRST));
        HelmChartDetailVO detail = new HelmChartDetailVO();
        detail.setRepositoryId(selected.getRepositoryId());
        detail.setRepositoryName(repository.getName());
        detail.setChartName(selected.getChartName());
        detail.setDescription(selected.getDescription());
        detail.setVersion(selected.getVersion());
        detail.setAppVersion(selected.getAppVersion());
        detail.setChartRef(selected.getChartRef());
        detail.setArtifactId(selected.getId());
        detail.setKeywords(HelmChartContent.keywords(selected.getKeywords()));
        detail.setReadme(selected.getReadme() == null ? "" : selected.getReadme());
        detail.setVersions(rows.stream().map(this::toVersionVO).toList());
        return detail;
    }

    public HelmValuesVO getValues(String chartName, String version, Long repositoryId) {
        if (version == null || version.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "version 不能为空");
        }
        HelmChartArtifactEntity selected = selectVersion(loadVersions(chartName, repositoryId), version);
        HelmChartRepositoryEntity repository = requireRepository(selected.getRepositoryId());
        ensureContent(selected, repository);
        HelmValuesVO vo = new HelmValuesVO();
        vo.setValuesYaml(selected.getValuesYaml() == null ? "" : selected.getValuesYaml());
        return vo;
    }

    public List<HelmChartArtifactVO> listVersions(String chartName, Long repositoryId) {
        List<HelmChartArtifactEntity> rows = loadVersions(chartName, repositoryId);
        rows.sort(Comparator.comparing(HelmChartArtifactEntity::getVersion, HelmVersions.NEWER_FIRST));
        Map<Long, String> names = repositoryNames();
        return rows.stream().map(row -> toArtifactVO(row, names.getOrDefault(row.getRepositoryId(), ""))).toList();
    }

    public HelmChartArtifactVO getVersion(String chartName, String version, Long repositoryId) {
        LambdaQueryWrapper<HelmChartArtifactEntity> wrapper = artifactFilter(repositoryId, chartName);
        wrapper.eq(HelmChartArtifactEntity::getVersion, version);
        List<HelmChartArtifactEntity> rows = artifactMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_NOT_FOUND);
        }
        if (rows.size() > 1) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID,
                    "多个仓库包含该版本，请指定 repositoryId");
        }
        HelmChartArtifactEntity row = rows.get(0);
        return toArtifactVO(row, repositoryName(row.getRepositoryId()));
    }

    private List<HelmChartArtifactEntity> loadVersions(String chartName, Long repositoryId) {
        return new ArrayList<>(artifactMapper.selectList(artifactFilter(repositoryId, chartName)));
    }

    private HelmChartArtifactEntity selectVersion(List<HelmChartArtifactEntity> rows, String version) {
        List<HelmChartArtifactEntity> matched = rows;
        if (version != null && !version.isBlank()) {
            matched = rows.stream().filter(row -> version.equals(row.getVersion())).toList();
        }
        if (matched.isEmpty()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_NOT_FOUND);
        }
        long repositories = matched.stream().map(HelmChartArtifactEntity::getRepositoryId).distinct().count();
        if (repositories > 1) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "多个仓库包含该 Chart，请指定 repositoryId");
        }
        return matched.stream()
                .min(Comparator.comparing(HelmChartArtifactEntity::getVersion, HelmVersions.NEWER_FIRST))
                .orElseThrow(() -> new BizException(ContainerErrorCode.HELM_CHART_NOT_FOUND));
    }

    private void ensureContent(HelmChartArtifactEntity entity, HelmChartRepositoryEntity repository) {
        if (Boolean.TRUE.equals(entity.getContentCached())) {
            return;
        }
        Path dir = null;
        try {
            dir = HelmTempFiles.createPrivateDir("helm-pull-");
            boolean plain = Boolean.TRUE.equals(repository.getInsecure()) || properties.getOci().isInsecure();
            Path archive = packagePuller.pull(new OciPullRequest(
                    entity.getChartRef(),
                    plain,
                    properties.getOci().getUsername(),
                    properties.getOci().getPassword(),
                    dir,
                    Duration.ofSeconds(Math.max(5, properties.getPushTimeoutSeconds()))));
            HelmChartPackageMeta meta = archiveInspector.inspect(archive, properties.archiveLimits());
            if (!entity.getChartName().equals(meta.name()) || !entity.getVersion().equals(meta.version())) {
                throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "拉取到的 Chart 与登记的名称或版本不一致");
            }
            HelmChartContent.apply(entity, meta);
            artifactMapper.updateById(entity);
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_PULL_FAILED, "无法准备 Chart 临时目录");
        } finally {
            HelmTempFiles.deleteRecursively(dir);
        }
    }

    public List<HelmChartRepositoryVO> listRepositories() {
        Long tenantId = tenantId();
        List<HelmChartRepositoryEntity> rows = repositoryMapper.selectList(
                new LambdaQueryWrapper<HelmChartRepositoryEntity>()
                        .eq(HelmChartRepositoryEntity::getTenantId, tenantId)
                        .orderByAsc(HelmChartRepositoryEntity::getName));
        return rows.stream().map(this::toRepositoryVO).toList();
    }

    private void assertAbsent(Long repositoryId, String chartName, String version) {
        Long count = artifactMapper.selectCount(new LambdaQueryWrapper<HelmChartArtifactEntity>()
                .eq(HelmChartArtifactEntity::getRepositoryId, repositoryId)
                .eq(HelmChartArtifactEntity::getChartName, chartName)
                .eq(HelmChartArtifactEntity::getVersion, version));
        if (count != null && count > 0) {
            throw new HelmChartConflictException("相同 Chart 名称与版本已存在");
        }
    }

    private HelmChartRepositoryEntity resolveRepository(Long repositoryId) {
        if (repositoryId != null) {
            return requireRepository(repositoryId);
        }
        return ensureDefaultRepository();
    }

    private HelmChartRepositoryEntity requireRepository(Long repositoryId) {
        HelmChartRepositoryEntity entity = repositoryMapper.selectById(repositoryId);
        if (entity == null || !tenantId().equals(entity.getTenantId())) {
            throw new BizException(ContainerErrorCode.HELM_CHART_REPOSITORY_NOT_FOUND);
        }
        if (!"oci".equals(entity.getType())) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "仅支持 OCI 仓库");
        }
        return entity;
    }

    private HelmChartRepositoryEntity ensureDefaultRepository() {
        HelmChartProperties.Oci oci = properties.getOci();
        String name = oci.getRepositoryName() == null || oci.getRepositoryName().isBlank()
                ? "default" : oci.getRepositoryName().trim();
        OciChartReference reference = OciChartReference.parse(oci.getUrl(), oci.isInsecure());
        Long tenantId = tenantId();
        HelmChartRepositoryEntity existing = repositoryMapper.selectOne(new LambdaQueryWrapper<HelmChartRepositoryEntity>()
                .eq(HelmChartRepositoryEntity::getTenantId, tenantId)
                .eq(HelmChartRepositoryEntity::getName, name));
        if (existing != null) {
            boolean changed = false;
            if (!reference.registryBase().equals(existing.getUrl())) {
                existing.setUrl(reference.registryBase());
                changed = true;
            }
            if (existing.getInsecure() == null || existing.getInsecure() != reference.plainHttp()) {
                existing.setInsecure(reference.plainHttp());
                changed = true;
            }
            if (changed) {
                repositoryMapper.updateById(existing);
            }
            return existing;
        }
        HelmChartRepositoryEntity created = new HelmChartRepositoryEntity();
        created.setTenantId(tenantId);
        created.setName(name);
        created.setType("oci");
        created.setUrl(reference.registryBase());
        created.setInsecure(reference.plainHttp());
        created.setCreatedAt(LocalDateTime.now());
        try {
            repositoryMapper.insert(created);
            return created;
        } catch (DataIntegrityViolationException ex) {
            HelmChartRepositoryEntity raced = repositoryMapper.selectOne(new LambdaQueryWrapper<HelmChartRepositoryEntity>()
                    .eq(HelmChartRepositoryEntity::getTenantId, tenantId)
                    .eq(HelmChartRepositoryEntity::getName, name));
            if (raced == null) {
                throw ex;
            }
            return raced;
        }
    }

    private LambdaQueryWrapper<HelmChartArtifactEntity> artifactFilter(Long repositoryId, String name) {
        LambdaQueryWrapper<HelmChartArtifactEntity> wrapper = new LambdaQueryWrapper<HelmChartArtifactEntity>()
                .eq(HelmChartArtifactEntity::getTenantId, tenantId())
                .eq(repositoryId != null, HelmChartArtifactEntity::getRepositoryId, repositoryId)
                .orderByDesc(HelmChartArtifactEntity::getCreatedAt);
        if (name != null && !name.isBlank()) {
            wrapper.eq(HelmChartArtifactEntity::getChartName, name.trim());
        }
        return wrapper;
    }

    private static Long tenantId() {
        Long current = SecurityHelper.currentTenantId();
        return current == null ? PLATFORM_TENANT_ID : current;
    }

    private static void assertTgzFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "文件名缺失");
        }
        String normalized = original.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = (slash >= 0 ? normalized.substring(slash + 1) : normalized).toLowerCase(Locale.ROOT);
        if (!base.endsWith(".tgz") || ".tgz".equals(base)) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "仅支持 .tgz Helm Chart 包");
        }
    }

    private static Path copyLimited(MultipartFile file, long maxBytes) throws IOException {
        Path temp = Files.createTempFile("helm-chart-", ".tgz");
        long total = 0;
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    Files.deleteIfExists(temp);
                    throw new BizException(ContainerErrorCode.HELM_CHART_TOO_LARGE, "文件超过大小限制");
                }
                out.write(buf, 0, n);
            }
        } catch (RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        if (total < 2 || !isGzip(temp)) {
            Files.deleteIfExists(temp);
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "文件不是 gzip 压缩的 Chart 包");
        }
        return temp;
    }

    private static boolean isGzip(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] magic = in.readNBytes(2);
            return magic.length == 2 && (magic[0] & 0xFF) == 0x1F && (magic[1] & 0xFF) == 0x8B;
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    digest.update(buf, 0, n);
                }
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Map<Long, String> repositoryNames() {
        List<HelmChartRepositoryEntity> rows = repositoryMapper.selectList(
                new LambdaQueryWrapper<HelmChartRepositoryEntity>()
                        .eq(HelmChartRepositoryEntity::getTenantId, tenantId()));
        Map<Long, String> names = new HashMap<>();
        if (rows == null) {
            return names;
        }
        for (HelmChartRepositoryEntity row : rows) {
            names.put(row.getId(), row.getName() == null ? "" : row.getName());
        }
        return names;
    }

    private String repositoryName(Long repositoryId) {
        return repositoryNames().getOrDefault(repositoryId, "");
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private HelmChartVersionVO toVersionVO(HelmChartArtifactEntity entity) {
        HelmChartVersionVO vo = new HelmChartVersionVO();
        vo.setArtifactId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setAppVersion(entity.getAppVersion());
        vo.setChartRef(entity.getChartRef());
        vo.setDigest(entity.getDigest());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private HelmChartArtifactVO toArtifactVO(HelmChartArtifactEntity entity, String repositoryName) {
        HelmChartArtifactVO vo = new HelmChartArtifactVO();
        vo.setId(entity.getId());
        vo.setRepositoryId(entity.getRepositoryId());
        vo.setRepositoryName(repositoryName == null ? "" : repositoryName);
        vo.setChartName(entity.getChartName());
        vo.setVersion(entity.getVersion());
        vo.setDigest(entity.getDigest());
        vo.setSizeBytes(entity.getSizeBytes());
        vo.setChartRef(entity.getChartRef());
        vo.setDescription(entity.getDescription());
        vo.setAppVersion(entity.getAppVersion());
        vo.setUploadedBy(entity.getUploadedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private HelmChartRepositoryVO toRepositoryVO(HelmChartRepositoryEntity entity) {
        HelmChartRepositoryVO vo = new HelmChartRepositoryVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setUrl(entity.getUrl());
        vo.setInsecure(entity.getInsecure());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
