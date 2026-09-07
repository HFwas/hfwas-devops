package com.hfwas.devops.image.history.service;

import com.hfwas.devops.image.dto.ImageConvertVO;
import com.hfwas.devops.image.dto.ImageHistoryVO;
import com.hfwas.devops.image.history.entity.ImageConvertHistoryEntity;
import com.hfwas.devops.image.history.mapper.ImageConvertHistoryMapper;
import com.hfwas.devops.image.service.ImageSession;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageHistoryServiceTest {

    @Mock
    private ImageConvertHistoryMapper mapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @InjectMocks
    private ImageHistoryService historyService;

    @Test
    void recordPersistsCurrentUserAndResultMetadata() {
        ImageSession session = ImageSession.builder()
                .sessionId("01TESTSESSIONIDABCDEFGHIJ")
                .originalFileName("shot.jpg")
                .mimeType("image/jpeg")
                .userId(7L)
                .tenantId(3L)
                .build();
        ImageConvertVO vo = ImageConvertVO.builder()
                .sessionId(session.getSessionId())
                .resultFileName("shot.png")
                .resultSize(1200)
                .mimeType("image/png")
                .width(80)
                .height(40)
                .strippedGps(true)
                .status("completed")
                .build();
        historyService.record(session, vo);
        ArgumentCaptor<ImageConvertHistoryEntity> captor = ArgumentCaptor.forClass(ImageConvertHistoryEntity.class);
        verify(mapper).insert(captor.capture());
        ImageConvertHistoryEntity row = captor.getValue();
        assertEquals(7L, row.getUserId());
        assertEquals(3L, row.getTenantId());
        assertEquals("shot.jpg", row.getFileName());
        assertEquals("image/png", row.getTargetFormat());
        assertEquals(1, row.getStrippedGps());
    }

    @Test
    void listRecentFiltersByCurrentUser() {
        when(currentUserAccessor.currentUserId()).thenReturn(7L);
        when(currentUserAccessor.currentTenantId()).thenReturn(3L);
        ImageConvertHistoryEntity row = new ImageConvertHistoryEntity();
        row.setId(1L);
        row.setSessionId("01ABC");
        row.setFileName("a.jpg");
        row.setStatus("completed");
        row.setCreateTime(LocalDateTime.of(2026, 9, 7, 12, 0));
        when(mapper.selectList(any())).thenReturn(List.of(row));
        List<ImageHistoryVO> list = historyService.listRecent(20);
        assertEquals(1, list.size());
        assertEquals("a.jpg", list.get(0).getFileName());
        assertEquals("completed", list.get(0).getStatus());
    }

    @Test
    void listRecentEmptyWhenAnonymous() {
        when(currentUserAccessor.currentUserId()).thenReturn(null);
        assertTrue(historyService.listRecent(10).isEmpty());
    }
}
