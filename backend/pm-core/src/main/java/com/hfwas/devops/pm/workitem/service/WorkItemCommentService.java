package com.hfwas.devops.pm.workitem.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.entity.PmWorkItemComment;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemCommentMapper;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import com.hfwas.devops.pm.workitem.model.WorkItemCommentVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkItemCommentService {

    private final PmWorkItemCommentMapper commentMapper;
    private final PmWorkItemMapper workItemMapper;
    private final CurrentUserAccessor currentUserAccessor;

    public List<WorkItemCommentVo> listByWorkItem(Long workItemId) {
        ensureWorkItemExists(workItemId);
        List<PmWorkItemComment> comments = commentMapper.selectList(
                Wrappers.<PmWorkItemComment>lambdaQuery()
                        .eq(PmWorkItemComment::getWorkItemId, workItemId)
                        .orderByAsc(PmWorkItemComment::getCreateTime)
        );
        return comments.stream().map(this::toVo).toList();
    }

    public long countByWorkItem(Long workItemId) {
        ensureWorkItemExists(workItemId);
        return commentMapper.selectCount(
                Wrappers.<PmWorkItemComment>lambdaQuery()
                        .eq(PmWorkItemComment::getWorkItemId, workItemId)
        );
    }

    public Map<String, Long> countByWorkItems(List<Long> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return Map.of();
        }
        List<PmWorkItemComment> comments = commentMapper.selectList(
                Wrappers.<PmWorkItemComment>lambdaQuery()
                        .in(PmWorkItemComment::getWorkItemId, workItemIds)
                        .select(PmWorkItemComment::getWorkItemId)
        );
        Map<Long, Long> grouped = comments.stream()
                .collect(Collectors.groupingBy(PmWorkItemComment::getWorkItemId, Collectors.counting()));
        Map<String, Long> result = new HashMap<>();
        for (Long id : workItemIds) {
            result.put(String.valueOf(id), grouped.getOrDefault(id, 0L));
        }
        return result;
    }

    @Transactional
    public Long save(Long workItemId, String content, Long parentId) {
        ensureWorkItemExists(workItemId);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (parentId != null) {
            PmWorkItemComment parent = commentMapper.selectById(parentId);
            if (parent == null || !workItemId.equals(parent.getWorkItemId())) {
                throw new IllegalArgumentException("回复目标不存在");
            }
        }
        PmWorkItemComment comment = new PmWorkItemComment();
        comment.setWorkItemId(workItemId);
        comment.setParentId(parentId);
        comment.setContent(content.trim());
        comment.setAuthorName(currentUserAccessor.currentDisplayName());
        commentMapper.insert(comment);
        return comment.getId();
    }

    @Transactional
    public void delete(Long id) {
        PmWorkItemComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        if (!canDelete(comment)) {
            throw new IllegalArgumentException("无权删除该评论");
        }
        commentMapper.deleteById(id);
    }

    private void ensureWorkItemExists(Long workItemId) {
        PmWorkItem item = workItemMapper.selectById(workItemId);
        if (item == null) {
            throw new IllegalArgumentException("事项不存在");
        }
    }

    private boolean canDelete(PmWorkItemComment comment) {
        Long userId = currentUserAccessor.currentUserId();
        if (userId == null) {
            return false;
        }
        if (currentUserAccessor.isAdmin()) {
            return true;
        }
        return comment.getCreateBy() != null && comment.getCreateBy().equals(userId);
    }

    private WorkItemCommentVo toVo(PmWorkItemComment comment) {
        WorkItemCommentVo vo = new WorkItemCommentVo();
        vo.setId(comment.getId());
        vo.setWorkItemId(comment.getWorkItemId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setAuthorName(comment.getAuthorName());
        vo.setAuthorId(comment.getCreateBy());
        vo.setCreateTime(comment.getCreateTime());
        vo.setDeletable(canDelete(comment));
        return vo;
    }
}
