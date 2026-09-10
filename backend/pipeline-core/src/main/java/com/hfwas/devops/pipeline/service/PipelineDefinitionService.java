package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelineJobDTO;
import com.hfwas.devops.pipeline.dto.PipelinePageQuery;
import com.hfwas.devops.pipeline.dto.PipelineSaveDTO;
import com.hfwas.devops.pipeline.dto.PipelineStageDTO;
import com.hfwas.devops.pipeline.dto.PipelineVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineGraphValidator;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.tekton.GitRemote;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PipelineDefinitionService {

    private final PipelineMapper pipelineMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final PipelineRunMapper runMapper;
    private final CurrentUserAccessor currentUserAccessor;

    public PipelineDefinitionService(
            PipelineMapper pipelineMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            PipelineRunMapper runMapper,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.pipelineMapper = pipelineMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.runMapper = runMapper;
        this.currentUserAccessor = currentUserAccessor;
    }

    public IPage<PipelineVO> page(PipelinePageQuery query) {
        Long tenantId = requireTenant();
        Page<PipelineEntity> page = new Page<>(query.resolvePageNo(), query.resolvePageSize());
        LambdaQueryWrapper<PipelineEntity> wrapper = new LambdaQueryWrapper<PipelineEntity>()
                .eq(PipelineEntity::getTenantId, tenantId)
                .orderByDesc(PipelineEntity::getUpdateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(PipelineEntity::getName, query.getKeyword().trim());
        }
        IPage<PipelineEntity> rows = pipelineMapper.selectPage(page, wrapper);
        return rows.convert(this::toListVo);
    }

    public PipelineVO get(Long id) {
        PipelineEntity row = requireOwned(id);
        PipelineVO vo = toListVo(row);
        vo.setStages(loadStages(id));
        return vo;
    }

    @Transactional
    public Long save(PipelineSaveDTO dto) {
        Long tenantId = requireTenant();
        if (!StringUtils.hasText(dto.getName())) {
            throw BizException.of(ResultCode.BAD_REQUEST, "名称不能为空");
        }
        PipelineGraphSpec graph = toGraph(dto);
        PipelineGraphValidator.validate(graph);
        boolean hasClone = graph.stages().stream()
                .flatMap(stage -> stage.jobs() == null ? java.util.stream.Stream.empty() : stage.jobs().stream())
                .anyMatch(job -> job.kind() == PipelineJobKind.CLONE);
        if (hasClone) {
            GitRemote.parse(dto.getRepoUrl());
        }

        PipelineEntity row = dto.getId() == null ? new PipelineEntity() : requireOwned(dto.getId());
        row.setTenantId(tenantId);
        row.setName(dto.getName().trim());
        row.setRepoUrl(StringUtils.hasText(dto.getRepoUrl()) ? dto.getRepoUrl().trim() : "");
        row.setGitRef(StringUtils.hasText(dto.getGitRef()) ? dto.getGitRef().trim() : "main");
        row.setCredentialId(dto.getCredentialId());
        row.setUpdateBy(currentUserAccessor.currentUserId());
        if (row.getId() == null) {
            row.setCreateBy(currentUserAccessor.currentUserId());
            pipelineMapper.insert(row);
        } else {
            pipelineMapper.updateById(row);
            stageMapper.delete(new LambdaQueryWrapper<PipelineStageEntity>()
                    .eq(PipelineStageEntity::getPipelineId, row.getId()));
            jobMapper.delete(new LambdaQueryWrapper<PipelineJobEntity>()
                    .eq(PipelineJobEntity::getPipelineId, row.getId()));
        }
        persistGraph(row.getId(), graph);
        return row.getId();
    }

    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        jobMapper.delete(new LambdaQueryWrapper<PipelineJobEntity>().eq(PipelineJobEntity::getPipelineId, id));
        stageMapper.delete(new LambdaQueryWrapper<PipelineStageEntity>().eq(PipelineStageEntity::getPipelineId, id));
        pipelineMapper.deleteById(id);
    }

    public PipelineGraphSpec loadGraph(Long pipelineId) {
        List<PipelineStageDTO> stages = loadStages(pipelineId);
        List<PipelineStageSpec> specs = new ArrayList<>();
        for (PipelineStageDTO stage : stages) {
            List<PipelineJobSpec> jobs = stage.getJobs().stream()
                    .map(job -> new PipelineJobSpec(
                            job.getId(),
                            job.getName(),
                            PipelineJobKind.valueOf(job.getKind()),
                            job.getCommand(),
                            job.getStack(),
                            job.getRuntimeVersion(),
                            job.getToolVersion(),
                            job.getSortOrder() == null ? 0 : job.getSortOrder()))
                    .toList();
            specs.add(new PipelineStageSpec(stage.getId(), stage.getName(), stage.getSortOrder(), jobs));
        }
        return new PipelineGraphSpec(specs);
    }

    public PipelineEntity requireOwned(Long id) {
        PipelineEntity row = pipelineMapper.selectById(id);
        if (row == null || !requireTenant().equals(row.getTenantId())) {
            throw BizException.of(ResultCode.NOT_FOUND, "流水线不存在");
        }
        return row;
    }

    private void persistGraph(Long pipelineId, PipelineGraphSpec graph) {
        List<PipelineStageSpec> stages = graph.stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageSpec::sortOrder))
                .toList();
        int stageOrder = 0;
        for (PipelineStageSpec stageSpec : stages) {
            PipelineStageEntity stage = new PipelineStageEntity();
            stage.setPipelineId(pipelineId);
            stage.setName(stageSpec.name());
            stage.setSortOrder(stageOrder++);
            stageMapper.insert(stage);
            int jobOrder = 0;
            List<PipelineJobSpec> jobs = stageSpec.jobs().stream()
                    .sorted(Comparator.comparingInt(PipelineJobSpec::sortOrder))
                    .toList();
            for (PipelineJobSpec jobSpec : jobs) {
                PipelineJobEntity job = new PipelineJobEntity();
                job.setPipelineId(pipelineId);
                job.setStageId(stage.getId());
                job.setName(jobSpec.name());
                job.setKind(jobSpec.kind().name());
                job.setCommand(jobSpec.command());
                job.setStack(jobSpec.stack());
                job.setRuntimeVersion(jobSpec.runtimeVersion());
                job.setToolVersion(jobSpec.toolVersion());
                job.setSortOrder(jobOrder++);
                jobMapper.insert(job);
            }
        }
    }

    private PipelineGraphSpec toGraph(PipelineSaveDTO dto) {
        if (dto.getStages() == null || dto.getStages().isEmpty()) {
            return new PipelineGraphSpec(List.of());
        }
        List<PipelineStageSpec> stages = new ArrayList<>();
        int index = 0;
        for (PipelineStageDTO stage : dto.getStages()) {
            List<PipelineJobSpec> jobs = new ArrayList<>();
            int jobIndex = 0;
            for (PipelineJobDTO job : stage.getJobs() == null ? List.<PipelineJobDTO>of() : stage.getJobs()) {
                jobs.add(new PipelineJobSpec(
                        job.getId(),
                        job.getName(),
                        parseJobKind(job.getKind()),
                        job.getCommand(),
                        job.getStack(),
                        job.getRuntimeVersion(),
                        job.getToolVersion(),
                        job.getSortOrder() == null ? jobIndex : job.getSortOrder()));
                jobIndex++;
            }
            stages.add(new PipelineStageSpec(
                    stage.getId(),
                    stage.getName(),
                    stage.getSortOrder() == null ? index : stage.getSortOrder(),
                    jobs));
            index++;
        }
        return new PipelineGraphSpec(stages);
    }

    private List<PipelineStageDTO> loadStages(Long pipelineId) {
        List<PipelineStageEntity> stages = stageMapper.selectList(new LambdaQueryWrapper<PipelineStageEntity>()
                .eq(PipelineStageEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineStageEntity::getSortOrder));
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));
        Map<Long, List<PipelineJobEntity>> byStage = jobs.stream()
                .collect(Collectors.groupingBy(PipelineJobEntity::getStageId));
        List<PipelineStageDTO> result = new ArrayList<>();
        for (PipelineStageEntity stage : stages) {
            PipelineStageDTO dto = new PipelineStageDTO();
            dto.setId(stage.getId());
            dto.setName(stage.getName());
            dto.setSortOrder(stage.getSortOrder());
            dto.setJobs(byStage.getOrDefault(stage.getId(), List.of()).stream().map(job -> {
                PipelineJobDTO item = new PipelineJobDTO();
                item.setId(job.getId());
                item.setName(job.getName());
                item.setKind(job.getKind());
                item.setCommand(job.getCommand());
                item.setStack(job.getStack());
                item.setRuntimeVersion(job.getRuntimeVersion());
                item.setToolVersion(job.getToolVersion());
                item.setSortOrder(job.getSortOrder());
                return item;
            }).toList());
            result.add(dto);
        }
        return result;
    }

    private PipelineVO toListVo(PipelineEntity row) {
        PipelineVO vo = new PipelineVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setRepoUrl(row.getRepoUrl());
        vo.setGitRef(row.getGitRef());
        vo.setCredentialId(row.getCredentialId());
        vo.setUpdateTime(row.getUpdateTime());
        PipelineRunEntity last = runMapper.selectOne(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getPipelineId, row.getId())
                .orderByDesc(PipelineRunEntity::getCreateTime)
                .last("LIMIT 1"));
        if (last != null) {
            vo.setLastRunId(last.getId());
            vo.setLastRunStatus(last.getStatus());
            vo.setLastRunTime(last.getCreateTime());
        }
        return vo;
    }

    private static PipelineJobKind parseJobKind(String kind) {
        try {
            return PipelineJobKind.valueOf(kind);
        } catch (Exception e) {
            throw BizException.of(ResultCode.BAD_REQUEST, "未知任务类型");
        }
    }

    private Long requireTenant() {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (tenantId == null) {
            throw BizException.of(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }
}
