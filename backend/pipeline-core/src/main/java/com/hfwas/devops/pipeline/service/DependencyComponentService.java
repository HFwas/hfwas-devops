package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pipeline.dto.DependencyComponentAggregateVO;
import com.hfwas.devops.pipeline.dto.DependencyComponentVO;
import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.mapper.DependencyComponentMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DependencyComponentService {

    private final DependencyComponentMapper componentMapper;
    private final PipelineRunMapper runMapper;
    private final PipelineMapper pipelineMapper;

    public DependencyComponentService(
            DependencyComponentMapper componentMapper,
            PipelineRunMapper runMapper,
            PipelineMapper pipelineMapper
    ) {
        this.componentMapper = componentMapper;
        this.runMapper = runMapper;
        this.pipelineMapper = pipelineMapper;
    }

    public IPage<DependencyComponentVO> pageComponents(int pageNo, int pageSize, String search, String language) {
        Page<DependencyComponentEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<DependencyComponentEntity> wrapper = new LambdaQueryWrapper<DependencyComponentEntity>()
                .orderByDesc(DependencyComponentEntity::getId);

        if (StringUtils.hasText(search)) {
            wrapper.and(w -> w
                    .like(DependencyComponentEntity::getName, search.trim())
                    .or().like(DependencyComponentEntity::getPurl, search.trim())
                    .or().like(DependencyComponentEntity::getGroupName, search.trim()));
        }
        if (StringUtils.hasText(language)) {
            wrapper.eq(DependencyComponentEntity::getLanguage, language.trim());
        }

        IPage<DependencyComponentEntity> rows = componentMapper.selectPage(page, wrapper);
        return rows.convert(this::toVo);
    }

    public IPage<DependencyComponentAggregateVO> pageAggregated(int pageNo, int pageSize, String search, String language) {
        // 查全部（简易实现：group by 聚合在内存中做；数据量大时改为 SQL group by）
        LambdaQueryWrapper<DependencyComponentEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(search)) {
            wrapper.and(w -> w
                    .like(DependencyComponentEntity::getName, search.trim())
                    .or().like(DependencyComponentEntity::getPurl, search.trim())
                    .or().like(DependencyComponentEntity::getGroupName, search.trim()));
        }
        if (StringUtils.hasText(language)) {
            wrapper.eq(DependencyComponentEntity::getLanguage, language.trim());
        }
        List<DependencyComponentEntity> all = componentMapper.selectList(wrapper);

        // 按 (purl, group, name, version) 聚合
        Map<String, List<DependencyComponentEntity>> grouped = all.stream()
                .collect(Collectors.groupingBy(c -> c.getPurl() + "|" + c.getName() + "|" + c.getVersion()));

        List<DependencyComponentAggregateVO> aggregated = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<DependencyComponentEntity> items = entry.getValue();
            DependencyComponentEntity first = items.getFirst();
            DependencyComponentAggregateVO vo = new DependencyComponentAggregateVO();
            vo.setPurl(first.getPurl());
            vo.setGroupName(first.getGroupName());
            vo.setName(first.getName());
            vo.setVersion(first.getVersion());
            vo.setLicense(first.getLicense());
            vo.setLanguage(first.getLanguage());
            vo.setOccurrenceCount(items.size());

            // 收集仓库名
            List<String> repos = items.stream()
                    .map(c -> {
                        PipelineRunEntity run = runMapper.selectById(c.getRunId());
                        if (run == null) return null;
                        PipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());
                        return pipeline != null ? pipeline.getName() : null;
                    })
                    .filter(n -> n != null)
                    .distinct()
                    .limit(3)
                    .toList();
            vo.setUsedInRepos(repos.toArray(new String[0]));
            aggregated.add(vo);
        }

        aggregated.sort(Comparator.comparingInt(DependencyComponentAggregateVO::getOccurrenceCount).reversed());

        // 手工分页
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, aggregated.size());
        List<DependencyComponentAggregateVO> pageContent = aggregated.subList(start, end);
        Page<DependencyComponentAggregateVO> result = new Page<>(pageNo, pageSize, aggregated.size());
        result.setRecords(pageContent);
        return result;
    }

    private DependencyComponentVO toVo(DependencyComponentEntity entity) {
        DependencyComponentVO vo = new DependencyComponentVO();
        vo.setId(entity.getId());
        vo.setArtifactId(entity.getArtifactId());
        vo.setRunId(entity.getRunId());
        vo.setPurl(entity.getPurl());
        vo.setGroupName(entity.getGroupName());
        vo.setName(entity.getName());
        vo.setVersion(entity.getVersion());
        vo.setLicense(entity.getLicense());
        vo.setScope(entity.getScope());
        vo.setLanguage(entity.getLanguage());
        vo.setCreateTime(entity.getCreateTime());

        // 补充流水线信息
        PipelineRunEntity run = runMapper.selectById(entity.getRunId());
        if (run != null) {
            PipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());
            if (pipeline != null) {
                vo.setPipelineId(pipeline.getId());
                vo.setPipelineName(pipeline.getName());
                vo.setRepoUrl(pipeline.getRepoUrl());
            }
        }

        return vo;
    }
}