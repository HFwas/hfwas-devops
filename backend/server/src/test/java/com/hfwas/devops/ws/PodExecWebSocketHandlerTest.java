package com.hfwas.devops.ws;

import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.service.PipelineDefinitionService;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-125、129..134 的可测分支：连接鉴权、归属校验与错误提示。
 */
@ExtendWith(MockitoExtension.class)
class PodExecWebSocketHandlerTest {

    @Mock
    private PipelineDefinitionService definitionService;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private PipelineRunJobMapper runJobMapper;
    @Mock
    private ObjectProvider<KubernetesClient> kubernetesClients;

    private PodExecWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PodExecWebSocketHandler(definitionService, runMapper, runJobMapper, kubernetesClients);
    }

    @AfterEach
    void tearDown() {
        ExecutorService scheduler = (ExecutorService) ReflectionTestUtils.getField(handler, "scheduler");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Test
    @DisplayName("PIPE-125: 未配置执行集群时返回错误并关闭")
    void noClusterSendsErrorAndCloses() throws Exception {
        when(kubernetesClients.getIfAvailable()).thenReturn(null);
        WebSocketSession session = session("ws://localhost/ws/exec/1/2/3");

        handler.afterConnectionEstablished(session);

        assertThat(capturedMessage(session)).contains("未配置执行集群");
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("PIPE-126: 路径不完整时返回错误")
    void incompletePathRejected() throws Exception {
        when(kubernetesClients.getIfAvailable()).thenReturn(mock(KubernetesClient.class));
        WebSocketSession session = session("ws://localhost/ws/exec/1/2");

        handler.afterConnectionEstablished(session);

        assertThat(capturedMessage(session)).contains("请求路径不完整");
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("PIPE-126: run 不属于 pipeline 时返回运行记录不存在")
    void runMismatchRejected() throws Exception {
        when(kubernetesClients.getIfAvailable()).thenReturn(mock(KubernetesClient.class));
        when(runMapper.selectById(2L)).thenReturn(null);
        WebSocketSession session = session("ws://localhost/ws/exec/1/2/3");

        handler.afterConnectionEstablished(session);

        assertThat(capturedMessage(session)).contains("运行记录不存在");
    }

    @Test
    @DisplayName("PIPE-126: job 不属于 run 时返回任务记录不存在")
    void jobMismatchRejected() throws Exception {
        when(kubernetesClients.getIfAvailable()).thenReturn(mock(KubernetesClient.class));
        when(runMapper.selectById(2L)).thenReturn(run(1L));
        when(runJobMapper.selectById(3L)).thenReturn(null);
        WebSocketSession session = session("ws://localhost/ws/exec/1/2/3");

        handler.afterConnectionEstablished(session);

        assertThat(capturedMessage(session)).contains("任务记录不存在");
    }

    @Test
    @DisplayName("PIPE-124: Pod 未分配时提示等待调度")
    void podNotAllocatedRejected() throws Exception {
        when(kubernetesClients.getIfAvailable()).thenReturn(mock(KubernetesClient.class));
        when(runMapper.selectById(2L)).thenReturn(run(1L));
        PipelineRunJobEntity job = new PipelineRunJobEntity();
        job.setId(3L);
        job.setRunId(2L);
        job.setPodName(null);
        job.setNamespace("hfwas-pipeline");
        when(runJobMapper.selectById(3L)).thenReturn(job);
        WebSocketSession session = session("ws://localhost/ws/exec/1/2/3");

        handler.afterConnectionEstablished(session);

        assertThat(capturedMessage(session)).contains("Pod 尚未分配");
    }

    @Test
    @DisplayName("PIPE-126: 非数字路径参数返回格式错误")
    void invalidPathParameterRejected() throws Exception {
        when(kubernetesClients.getIfAvailable()).thenReturn(mock(KubernetesClient.class));
        WebSocketSession session = session("ws://localhost/ws/exec/abc/2/3");

        handler.afterConnectionEstablished(session);

        assertThat(capturedMessage(session)).contains("路径参数格式错误");
    }

    @Test
    @DisplayName("PIPE-129: 未建立会话的文本消息被忽略")
    void textMessageWithoutSessionIsIgnored() throws Exception {
        WebSocketSession session = session("ws://localhost/ws/exec/1/2/3");

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"input\",\"data\":\"ls\"}"));

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("PIPE-129: 未知会话关闭时安全忽略")
    void closeUnknownSessionIsNoop() throws Exception {
        WebSocketSession session = session("ws://localhost/ws/exec/1/2/3");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(session, never()).close(any(CloseStatus.class));
    }

    private String capturedMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }

    private static WebSocketSession session(String uri) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        org.mockito.Mockito.lenient().when(session.getId()).thenReturn("s-1");
        org.mockito.Mockito.lenient().when(session.getUri()).thenReturn(URI.create(uri));
        org.mockito.Mockito.lenient().when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("userId", "7")));
        org.mockito.Mockito.lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static PipelineRunEntity run(Long pipelineId) {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(2L);
        run.setPipelineId(pipelineId);
        run.setTektonName("demo-run");
        return run;
    }
}
