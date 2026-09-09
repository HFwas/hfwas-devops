package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.TaskKindUpdateDTO;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindEntity;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PipelineTaskKindService {

    private final PipelineTaskKindMapper mapper;

    public PipelineTaskKindService(PipelineTaskKindMapper mapper) {
        this.mapper = mapper;
    }

    /** 返回所有记录（含禁用的），供管理页 */
    public List<TaskKindVO> listAll() {
        return mapper.selectList(null).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 仅返回已启用的，供编辑器/选择器使用 */
    public List<TaskKindVO> listEnabled() {
        return mapper.selectEnabled().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 获取单个 Task 定义 */
    public TaskKindVO getByKind(String kind) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        return toVO(entity);
    }

    @Transactional
    public void update(String kind, TaskKindUpdateDTO dto) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        entity.setLabel(dto.getLabel());
        entity.setDescription(dto.getDescription());
        entity.setHint(dto.getHint());
        entity.setDefaultCommand(dto.getDefaultCommand());
        // toolImage 仅在前端显式传入时才更新（为空时不覆盖）
        if (dto.getToolImage() != null) {
            entity.setToolImage(dto.getToolImage());
        }
        entity.setCommandTemplate(dto.getCommandTemplate());
        entity.setSortOrder(dto.getSortOrder());
        mapper.updateById(entity);
    }

    @Transactional
    public void toggle(String kind) {
        PipelineTaskKindEntity entity = mapper.selectByKind(kind);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: " + kind);
        }
        entity.setEnabled(entity.getEnabled() == 1 ? 0 : 1);
        mapper.updateById(entity);
    }

    private TaskKindVO toVO(PipelineTaskKindEntity entity) {
        TaskKindVO vo = new TaskKindVO();
        vo.setKindValue(entity.getKindValue());
        vo.setLabel(entity.getLabel());
        vo.setTaskGroup(entity.getTaskGroup());
        vo.setDescription(entity.getDescription());
        vo.setHint(entity.getHint());
        vo.setDefaultCommand(entity.getDefaultCommand());
        vo.setRequiresCommand(entity.getRequiresCommand() == 1);
        vo.setEnabled(entity.getEnabled() == 1);
        vo.setSortOrder(entity.getSortOrder());
        vo.setToolImage(entity.getToolImage());
        vo.setDefaultImage(entity.getDefaultImage());
        vo.setCommandTemplate(entity.getCommandTemplate());
        return vo;
    }
}