package com.hfwas.devops.image.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.image.dto.ImageConvertVO;
import com.hfwas.devops.image.dto.ImageHistoryVO;
import com.hfwas.devops.image.history.entity.ImageConvertHistoryEntity;
import com.hfwas.devops.image.history.mapper.ImageConvertHistoryMapper;
import com.hfwas.devops.image.service.ImageSession;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
public class ImageHistoryService {

    private final ImageConvertHistoryMapper mapper;
    private final CurrentUserAccessor currentUserAccessor;

    public ImageHistoryService(ImageConvertHistoryMapper mapper, CurrentUserAccessor currentUserAccessor) {
        this.mapper = mapper;
        this.currentUserAccessor = currentUserAccessor;
    }

    public void record(ImageSession session, ImageConvertVO vo) {
        if (mapper == null || session == null || vo == null) {
            return;
        }
        try {
            ImageConvertHistoryEntity row = new ImageConvertHistoryEntity();
            row.setSessionId(session.getSessionId());
            row.setUserId(session.getUserId());
            row.setTenantId(session.getTenantId());
            row.setFileName(session.getOriginalFileName());
            row.setSourceMime(session.getMimeType());
            row.setTargetFormat(vo.getMimeType());
            row.setResultFileName(vo.getResultFileName());
            row.setResultSize(vo.getResultSize());
            row.setWidth(vo.getWidth());
            row.setHeight(vo.getHeight());
            row.setStrippedGps(vo.isStrippedGps() ? 1 : 0);
            row.setStatus(vo.getStatus() == null ? "completed" : vo.getStatus());
            row.setErrorMessage(vo.getErrorMessage());
            row.setDeleted(0);
            row.setCreateBy(session.getUserId());
            mapper.insert(row);
        } catch (Exception e) {
            log.warn("image history persist failed: {}", e.getMessage());
        }
    }

    public List<ImageHistoryVO> listRecent(int limit) {
        if (mapper == null) {
            return List.of();
        }
        Long userId = currentUserAccessor.currentUserId();
        if (userId == null) {
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), 100);
        List<ImageConvertHistoryEntity> rows = mapper.selectList(new LambdaQueryWrapper<ImageConvertHistoryEntity>()
                .eq(ImageConvertHistoryEntity::getUserId, userId)
                .orderByDesc(ImageConvertHistoryEntity::getCreateTime)
                .last("LIMIT " + size));
        return rows.stream().map(this::toVo).toList();
    }

    private ImageHistoryVO toVo(ImageConvertHistoryEntity row) {
        return ImageHistoryVO.builder()
                .id(row.getId())
                .sessionId(row.getSessionId())
                .fileName(row.getFileName())
                .sourceMime(row.getSourceMime())
                .targetFormat(row.getTargetFormat())
                .resultFileName(row.getResultFileName())
                .resultSize(row.getResultSize())
                .width(row.getWidth())
                .height(row.getHeight())
                .strippedGps(row.getStrippedGps() != null && row.getStrippedGps() == 1)
                .status(row.getStatus())
                .createTime(row.getCreateTime() == null ? null : row.getCreateTime().toInstant(ZoneOffset.UTC))
                .build();
    }
}
