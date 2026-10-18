package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.JobParamBindingDTO;
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
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-001..020、PIPE-151：流水线定义、校验与分页。
 */
@ExtendWith(MockitoExtension.class)
class PipelineDefinitionServiceTest {

    @Mock
    private PipelineMapper pipelineMapper;
    @Mock
    private PipelineStageMapper stageMapper;
    @Mock
    private PipelineJobMapper jobMapper;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;

    private PipelineDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new PipelineDefinitionService(pipelineMapper, stageMapper, jobMapper, runMapper, currentUserAccessor);
        lenient().when(currentUserAccessor.currentTenantId()).thenReturn(9L);
        lenient().when(currentUserAccessor.currentUserId()).thenReturn(7L);
    }

    // ---- page / get ----

    @Test
    @DisplayName("PIPE-017/018: 分页使用当前租户、默认分页与关键词")
    void pageUsesTenantDefaultsAndKeyword() {
        Page<PipelineEntity> result = new Page<>(1, 10);
        result.setRecords(List.of(pipeline(1L, "demo")));
        when(pipelineMapper.selectPage(any(), any())).thenReturn(result);
        when(stageMapper.selectList(any())).thenReturn(List.of());
        when(jobMapper.selectList(any())).thenReturn(List.of());
        when(runMapper.selectOne(any())).thenReturn(null);

        PipelinePageQuery query = new PipelinePageQuery();
        query.setKeyword("  demo  ");
        IPage<PipelineVO> page = service.page(query);

        assertThat(page.getRecords()).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<PipelineEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(pipelineMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("PIPE-017: pageSize 上限为 100、下限为 10")
    void pageSizeIsCapped() {
        PipelinePageQuery query = new PipelinePageQuery();
        query.setPageNo(0);
        query.setPageSize(500);
        assertThat(query.resolvePageNo()).isEqualTo(1);
        assertThat(query.resolvePageSize()).isEqualTo(100);

        query.setPageSize(0);
        assertThat(query.resolvePageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("PIPE-001: 详情返回阶段、任务与最近运行")
    void getReturnsStagesAndLastRun() {
        PipelineEntity row = pipeline(1L, "demo");
        when(pipelineMapper.selectById(1L)).thenReturn(row);
        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(11L);
        stage.setPipelineId(1L);
        stage.setName("build");
        stage.setSortOrder(0);
        when(stageMapper.selectList(any())).thenReturn(List.of(stage));
        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(21L);
        job.setPipelineId(1L);
        job.setStageId(11L);
        job.setName("mvn");
        job.setKind("BUILD");
        job.setCommand("mvn package");
        job.setSortOrder(0);
        job.setParamBindings("{\"VERSION\":{\"mode\":\"fixed\",\"value\":\"1\"}}");
        when(jobMapper.selectList(any())).thenReturn(List.of(job));
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(31L);
        run.setStatus("SUCCEEDED");
        run.setCreateTime(LocalDateTime.now());
        when(runMapper.selectOne(any())).thenReturn(run);

        PipelineVO vo = service.get(1L);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getStages()).hasSize(1);
        assertThat(vo.getStages().getFirst().getJobs()).hasSize(1);
        JobParamBindingDTO binding = vo.getStages().getFirst().getJobs().getFirst().getParamBindings().get("VERSION");
        assertThat(binding.getMode()).isEqualTo("fixed");
        assertThat(binding.getValue()).isEqualTo("1");
        assertThat(vo.getLastRunId()).isEqualTo(31L);
        assertThat(vo.getLastRunStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("PIPE-151: 跨租户详情返回 10003")
    void getRejectsOtherTenant() {
        PipelineEntity row = pipeline(1L, "demo");
        row.setTenantId(8L);
        when(pipelineMapper.selectById(1L)).thenReturn(row);

        BizException ex = assertThrows(BizException.class, () -> service.get(1L));
        assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("PIPE-151: 缺少租户上下文返回 11010")
    void getRejectsMissingTenantContext() {
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline(1L, "demo"));
        when(currentUserAccessor.currentTenantId()).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.get(1L));
        assertThat(ex.getCode()).isEqualTo(ResultCode.TENANT_CONTEXT_MISSING.getCode());
    }

    // ---- create / update ----

    @Test
    @DisplayName("PIPE-001/002: 创建最小流水线并回填 id")
    void createPersistsPipelineStagesAndJobs() {
        PipelineSaveDTO dto = dto("demo", "https://git.example.com/a/b.git",
                stage("build", job("mvn", "BUILD", "mvn package")));
        AtomicLong ids = new AtomicLong(100);
        when(pipelineMapper.insert(any(PipelineEntity.class))).thenAnswer(inv -> {
            inv.<PipelineEntity>getArgument(0).setId(1L);
            return 1;
        });
        when(stageMapper.insert(any(PipelineStageEntity.class))).thenAnswer(inv -> {
            inv.<PipelineStageEntity>getArgument(0).setId(ids.incrementAndGet());
            return 1;
        });
        when(jobMapper.insert(any(PipelineJobEntity.class))).thenAnswer(inv -> {
            inv.<PipelineJobEntity>getArgument(0).setId(ids.incrementAndGet());
            return 1;
        });

        assertThat(service.save(dto)).isEqualTo(1L);

        ArgumentCaptor<PipelineEntity> pipeline = ArgumentCaptor.forClass(PipelineEntity.class);
        verify(pipelineMapper).insert(pipeline.capture());
        assertThat(pipeline.getValue().getTenantId()).isEqualTo(9L);
        assertThat(pipeline.getValue().getName()).isEqualTo("demo");
        assertThat(pipeline.getValue().getGitRef()).isEqualTo("main");
        assertThat(pipeline.getValue().getCreateBy()).isEqualTo(7L);

        ArgumentCaptor<PipelineStageEntity> stage = ArgumentCaptor.forClass(PipelineStageEntity.class);
        verify(stageMapper).insert(stage.capture());
        assertThat(stage.getValue().getPipelineId()).isEqualTo(1L);
        assertThat(stage.getValue().getSortOrder()).isZero();

        ArgumentCaptor<PipelineJobEntity> job = ArgumentCaptor.forClass(PipelineJobEntity.class);
        verify(jobMapper).insert(job.capture());
        assertThat(job.getValue().getStageId()).isEqualTo(stage.getValue().getId());
        assertThat(job.getValue().getKind()).isEqualTo("BUILD");
        assertThat(job.getValue().getSortOrder()).isZero();
    }

    @Test
    @DisplayName("PIPE-001: 名称前后空格被裁剪")
    void createTrimsName() {
        PipelineSaveDTO dto = dto("  demo  ", null, stage("s", job("j", "BUILD", "make")));
        when(pipelineMapper.insert(any(PipelineEntity.class))).thenAnswer(inv -> {
            inv.<PipelineEntity>getArgument(0).setId(1L);
            return 1;
        });
        when(stageMapper.insert(any(PipelineStageEntity.class))).thenAnswer(inv -> {
            inv.<PipelineStageEntity>getArgument(0).setId(11L);
            return 1;
        });

        service.save(dto);

        ArgumentCaptor<PipelineEntity> captor = ArgumentCaptor.forClass(PipelineEntity.class);
        verify(pipelineMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("demo");
    }

    @Test
    @DisplayName("PIPE-003: 名称为空返回 10002")
    void createRejectsBlankName() {
        PipelineSaveDTO dto = dto("  ", null, stage("s", job("j", "BUILD", "make")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(ex.getMessage()).contains("名称不能为空");
        verify(pipelineMapper, never()).insert(any(PipelineEntity.class));
    }

    @Test
    @DisplayName("PIPE-007: 未知任务类型返回 10002")
    void createRejectsUnknownKind() {
        PipelineSaveDTO dto = dto("demo", null, stage("s", job("j", "NOT_A_KIND", "make")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("未知任务类型");
    }

    @Test
    @DisplayName("PIPE-008: 空阶段列表返回 10002")
    void createRejectsEmptyStages() {
        PipelineSaveDTO dto = dto("demo", null);
        dto.setStages(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("至少需要一个阶段");
    }

    @Test
    @DisplayName("PIPE-009: 阶段无任务返回 10002")
    void createRejectsStageWithoutJobs() {
        PipelineSaveDTO dto = dto("demo", null, stage("empty"));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("至少需要一个任务");
    }

    @Test
    @DisplayName("PIPE-010: 阶段名称不能为空")
    void createRejectsBlankStageName() {
        PipelineSaveDTO dto = dto("demo", null, stage("  ", job("j", "BUILD", "make")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("阶段名称不能为空");
    }

    @Test
    @DisplayName("PIPE-010: 任务名称不能为空")
    void createRejectsBlankJobName() {
        PipelineSaveDTO dto = dto("demo", null, stage("s", job("  ", "BUILD", "make")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("任务名称不能为空");
    }

    @Test
    @DisplayName("PIPE-011: 多个 CLONE 返回 10002")
    void createRejectsSecondClone() {
        PipelineSaveDTO dto = dto("demo", "https://git.example.com/a/b.git",
                stage("s1", job("c1", "CLONE", "")),
                stage("s2", job("c2", "CLONE", "")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("至多一个 clone");
    }

    @Test
    @DisplayName("PIPE-012: 审批与其它任务同阶段返回 10002")
    void createRejectsApprovalSharingStage() {
        PipelineSaveDTO dto = dto("demo", null,
                stage("s", job("a", "APPROVAL", ""), job("b", "BUILD", "make")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("审批任务必须独占一列");
    }

    @Test
    @DisplayName("PIPE-013: 同阶段两个 IMAGE 返回 10002")
    void createRejectsParallelImage() {
        PipelineSaveDTO dto = dto("demo", null,
                stage("s", job("i1", "IMAGE", "build"), job("i2", "IMAGE", "build")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("镜像构建不能与其它镜像构建并行");
    }

    @Test
    @DisplayName("PIPE-014: 需要命令的任务为空返回 10002")
    void createRejectsMissingCommand() {
        PipelineSaveDTO dto = dto("demo", null, stage("s", job("build", "BUILD", "  ")));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getMessage()).contains("需要命令");
    }

    @Test
    @DisplayName("PIPE-004/005: 含 CLONE 时 repoUrl 必须为合法 http(s) 地址")
    void createWithCloneRequiresValidRepoUrl() {
        PipelineSaveDTO missing = dto("demo", "", stage("s", job("clone", "CLONE", "")));
        BizException ex1 = assertThrows(BizException.class, () -> service.save(missing));
        assertThat(ex1.getMessage()).isNotBlank();

        PipelineSaveDTO invalid = dto("demo", "not-a-url", stage("s", job("clone", "CLONE", "")));
        BizException ex2 = assertThrows(BizException.class, () -> service.save(invalid));
        assertThat(ex2.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("PIPE-006: 无 CLONE 时允许空 repoUrl")
    void createWithoutCloneAllowsEmptyRepoUrl() {
        PipelineSaveDTO dto = dto("demo", "", stage("s", job("build", "BUILD", "make")));
        when(pipelineMapper.insert(any(PipelineEntity.class))).thenAnswer(inv -> {
            inv.<PipelineEntity>getArgument(0).setId(1L);
            return 1;
        });
        when(stageMapper.insert(any(PipelineStageEntity.class))).thenAnswer(inv -> {
            inv.<PipelineStageEntity>getArgument(0).setId(11L);
            return 1;
        });

        assertThat(service.save(dto)).isEqualTo(1L);
        ArgumentCaptor<PipelineEntity> captor = ArgumentCaptor.forClass(PipelineEntity.class);
        verify(pipelineMapper).insert(captor.capture());
        assertThat(captor.getValue().getRepoUrl()).isEmpty();
    }

    @Test
    @DisplayName("PIPE-016: 更新先删除旧阶段/任务再重建")
    void updateDeletesChildrenBeforePersist() {
        PipelineEntity existing = pipeline(1L, "old");
        when(pipelineMapper.selectById(1L)).thenReturn(existing);
        when(stageMapper.insert(any(PipelineStageEntity.class))).thenAnswer(inv -> {
            inv.<PipelineStageEntity>getArgument(0).setId(11L);
            return 1;
        });
        PipelineSaveDTO dto = dto("new", null, stage("s", job("j", "BUILD", "make")));
        dto.setId(1L);

        assertThat(service.save(dto)).isEqualTo(1L);

        verify(stageMapper).delete(any());
        verify(jobMapper).delete(any());
        verify(pipelineMapper).updateById(existing);
        assertThat(existing.getName()).isEqualTo("new");
    }

    @Test
    @DisplayName("PIPE-151: 更新他人流水线返回 10003")
    void updateRejectsOtherTenant() {
        PipelineEntity existing = pipeline(1L, "old");
        existing.setTenantId(8L);
        when(pipelineMapper.selectById(1L)).thenReturn(existing);
        PipelineSaveDTO dto = dto("new", null, stage("s", job("j", "BUILD", "make")));
        dto.setId(1L);

        BizException ex = assertThrows(BizException.class, () -> service.save(dto));
        assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        verify(pipelineMapper, never()).updateById(any(PipelineEntity.class));
    }

    // ---- delete ----

    @Test
    @DisplayName("PIPE-019: 删除流水线先删任务与阶段")
    void deleteRemovesChildrenAndPipeline() {
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline(1L, "demo"));

        service.delete(1L);

        verify(jobMapper).delete(any());
        verify(stageMapper).delete(any());
        verify(pipelineMapper).deleteById(1L);
    }

    @Test
    @DisplayName("PIPE-020: 删除不存在/他人流水线返回 10003")
    void deleteRejectsOtherTenant() {
        when(pipelineMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L));
        assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        verify(pipelineMapper, never()).deleteById(anyLong());
    }

    // ---- loadGraph ----

    @Test
    @DisplayName("PIPE-016: loadGraph 还原阶段/任务/参数绑定")
    void loadGraphMapsEntities() {
        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(11L);
        stage.setPipelineId(1L);
        stage.setName("build");
        stage.setSortOrder(0);
        when(stageMapper.selectList(any())).thenReturn(List.of(stage));
        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(21L);
        job.setPipelineId(1L);
        job.setStageId(11L);
        job.setName("mvn");
        job.setKind("BUILD");
        job.setCommand("mvn package");
        job.setSortOrder(0);
        when(jobMapper.selectList(any())).thenReturn(List.of(job));

        PipelineGraphSpec graph = service.loadGraph(1L);

        assertThat(graph.stages()).hasSize(1);
        assertThat(graph.stages().getFirst().jobs()).hasSize(1);
        assertThat(graph.stages().getFirst().jobs().getFirst().command()).isEqualTo("mvn package");
    }

    @Test
    @DisplayName("PIPE-007: loadGraph 遇到非法 kind 返回 10002")
    void loadGraphRejectsUnknownKind() {
        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(11L);
        stage.setPipelineId(1L);
        stage.setName("s");
        when(stageMapper.selectList(any())).thenReturn(List.of(stage));
        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(21L);
        job.setPipelineId(1L);
        job.setStageId(11L);
        job.setKind("UNKNOWN");
        job.setName("j");
        when(jobMapper.selectList(any())).thenReturn(List.of(job));

        assertThrows(IllegalArgumentException.class, () -> service.loadGraph(1L));
    }

    // ---- fixtures ----

    private static PipelineEntity pipeline(Long id, String name) {
        PipelineEntity row = new PipelineEntity();
        row.setId(id);
        row.setTenantId(9L);
        row.setName(name);
        row.setRepoUrl("");
        row.setGitRef("main");
        return row;
    }

    private static PipelineSaveDTO dto(String name, String repoUrl, PipelineStageDTO... stages) {
        PipelineSaveDTO dto = new PipelineSaveDTO();
        dto.setName(name);
        dto.setRepoUrl(repoUrl);
        dto.setStages(List.of(stages));
        return dto;
    }

    private static PipelineStageDTO stage(String name, PipelineJobDTO... jobs) {
        PipelineStageDTO stage = new PipelineStageDTO();
        stage.setName(name);
        stage.setJobs(List.of(jobs));
        return stage;
    }

    private static PipelineJobDTO job(String name, String kind, String command) {
        PipelineJobDTO job = new PipelineJobDTO();
        job.setName(name);
        job.setKind(kind);
        job.setCommand(command);
        return job;
    }
}
