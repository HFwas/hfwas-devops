package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.workitem.entity.PmProjectSequence;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmProjectSequenceMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkItemSequenceService {

    private final PmProjectSequenceMapper sequenceMapper;
    private final PmWorkItemMapper workItemMapper;

    @Transactional
    public int nextItemNo(Long projectId) {
        PmProjectSequence sequence = sequenceMapper.selectById(projectId);
        if (sequence == null) {
            int start = resolveStartItemNo(projectId);
            sequence = new PmProjectSequence();
            sequence.setProjectId(projectId);
            sequence.setNextItemNo(start);
            sequenceMapper.insert(sequence);
        }
        int itemNo = sequence.getNextItemNo();
        sequence.setNextItemNo(itemNo + 1);
        sequenceMapper.updateById(sequence);
        return itemNo;
    }

    public void syncSequence(Long projectId) {
        int next = resolveStartItemNo(projectId);
        PmProjectSequence sequence = sequenceMapper.selectById(projectId);
        if (sequence == null) {
            sequence = new PmProjectSequence();
            sequence.setProjectId(projectId);
            sequence.setNextItemNo(next);
            sequenceMapper.insert(sequence);
        } else if (sequence.getNextItemNo() < next) {
            sequence.setNextItemNo(next);
            sequenceMapper.updateById(sequence);
        }
    }

    private int resolveStartItemNo(Long projectId) {
        PmWorkItem latest = workItemMapper.selectOne(
                Wrappers.<PmWorkItem>lambdaQuery()
                        .eq(PmWorkItem::getProjectId, projectId)
                        .isNotNull(PmWorkItem::getItemNo)
                        .orderByDesc(PmWorkItem::getItemNo)
                        .last("LIMIT 1")
        );
        return latest == null || latest.getItemNo() == null ? 1 : latest.getItemNo() + 1;
    }
}
