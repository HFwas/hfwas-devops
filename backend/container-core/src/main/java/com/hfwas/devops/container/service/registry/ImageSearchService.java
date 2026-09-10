package com.hfwas.devops.container.service.registry;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.container.adapter.RegistryAdapter;
import com.hfwas.devops.container.dto.ImageSearchVO;
import com.hfwas.devops.container.dto.RegistryRepoVO;
import com.hfwas.devops.container.entity.RegistryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for cross-registry image search.
 * <p>
 * Iterates all registries → projects → repositories to build a unified image list.
 * Keyword filtering and pagination are performed in-memory on the aggregated results.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    private final RegistryService registryService;

    /**
     * Search images across all registries accessible to the current tenant.
     *
     * @param keyword  optional image name filter (case-insensitive contains match on repoName)
     * @param pageNo   page number (1-based)
     * @param pageSize page size
     * @return paginated search results
     */
    public IPage<ImageSearchVO> search(String keyword, int pageNo, int pageSize) {
        List<ImageSearchVO> all = listAllImages();
        // Apply keyword filter
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            all = all.stream()
                    .filter(vo -> vo.getRepoName() != null && vo.getRepoName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }
        // Sort by updateTime descending (nulls last)
        all.sort(Comparator.nullsLast(
                Comparator.<ImageSearchVO, String>comparing(ImageSearchVO::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))));

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        if (from >= total) {
            Page<ImageSearchVO> page = new Page<>(pageNo, pageSize, total);
            page.setRecords(List.of());
            return page;
        }
        int to = Math.min(from + pageSize, total);
        List<ImageSearchVO> records = all.subList(from, to);

        Page<ImageSearchVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    /**
     * Aggregate all images (repositories) from every registry and project.
     */
    private List<ImageSearchVO> listAllImages() {
        List<RegistryEntity> registries = registryService.listByTenant();
        if (registries.isEmpty()) {
            return new ArrayList<>();
        }

        List<ImageSearchVO> results = new ArrayList<>();
        for (RegistryEntity registry : registries) {
            try {
                RegistryAdapter adapter = registryService.buildAdapter(registry);
                var projects = adapter.listProjects();
                if (projects == null || projects.isEmpty()) {
                    continue;
                }
                for (var project : projects) {
                    String projectName = project.getName();
                    if (projectName == null || projectName.isBlank()) {
                        continue;
                    }
                    try {
                        // Fetch first page of repos per project (up to 100)
                        List<RegistryRepoVO> repos = adapter.listRepositories(projectName, 1, 100);
                        if (repos == null || repos.isEmpty()) {
                            continue;
                        }
                        for (RegistryRepoVO repo : repos) {
                            ImageSearchVO vo = new ImageSearchVO();
                            vo.setRegistryId(registry.getId());
                            vo.setRegistryName(registry.getAlias() != null ? registry.getAlias() : registry.getName());
                            vo.setRegistryUrl(registry.getUrl());
                            vo.setProjectName(projectName);
                            vo.setRepoName(repo.getName());
                            vo.setArtifactCount(repo.getArtifactCount());
                            vo.setPullCount(repo.getPullCount());
                            vo.setUpdateTime(repo.getUpdateTime());
                            results.add(vo);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to list repos for registry={} project={}: {}",
                                registry.getId(), projectName, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to process registry {} ({}): {}",
                        registry.getId(), registry.getName(), e.getMessage());
            }
        }
        return results;
    }
}