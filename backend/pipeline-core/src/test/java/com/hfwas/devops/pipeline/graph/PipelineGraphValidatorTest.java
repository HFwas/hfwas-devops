package com.hfwas.devops.pipeline.graph;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineGraphValidatorTest {

    @Test
    void rejectsEmptyGraph() {
        BizException ex = assertThrows(BizException.class, () -> PipelineGraphValidator.validate(new PipelineGraphSpec(List.of())));
        assertEquals("流水线至少需要一个阶段", ex.getMessage());
    }

    @Test
    void defaultGraphIsValidAndHasCloneFirst() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "clone", 0, List.of(
                        new PipelineJobSpec(null, "clone", PipelineJobKind.CLONE, "", null, null, null, 0)
                )),
                new PipelineStageSpec(null, "build", 1, List.of(
                        new PipelineJobSpec(null, "build", PipelineJobKind.BUILD,
                                "mvn -B -DskipTests package", "JAVA_MAVEN", "21", "3.9", 0)
                )),
                new PipelineStageSpec(null, "test", 2, List.of(
                        new PipelineJobSpec(null, "test", PipelineJobKind.TEST,
                                "mvn -B test", "JAVA_MAVEN", "21", "3.9", 0)
                ))
        ));
        PipelineGraphValidator.validate(graph);
        assertEquals(3, graph.stages().size());
        assertEquals(PipelineJobKind.CLONE, graph.stages().getFirst().jobs().getFirst().kind());
    }

    @Test
    void allowsShellOnlyWithoutClone() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "run", 0, List.of(
                        new PipelineJobSpec(null, "echo", PipelineJobKind.CUSTOM, "echo ok", null, null, null, 0)
                ))
        ));
        PipelineGraphValidator.validate(graph);
    }

    @Test
    void rejectsSecondClone() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "a", 0, List.of(
                        new PipelineJobSpec(null, "c1", PipelineJobKind.CLONE, "", null, null, null, 0)
                )),
                new PipelineStageSpec(null, "b", 1, List.of(
                        new PipelineJobSpec(null, "c2", PipelineJobKind.CLONE, "", null, null, null, 0)
                ))
        ));
        BizException ex = assertThrows(BizException.class, () -> PipelineGraphValidator.validate(graph));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("流水线至多一个 clone 任务", ex.getMessage());
    }

    @Test
    void rejectsApprovalSharingColumn() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "gate", 0, List.of(
                        new PipelineJobSpec(null, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0),
                        new PipelineJobSpec(null, "echo", PipelineJobKind.CUSTOM, "echo", null, null, null, 1)
                ))
        ));
        BizException ex = assertThrows(BizException.class, () -> PipelineGraphValidator.validate(graph));
        assertEquals("审批任务必须独占一列", ex.getMessage());
    }

    @Test
    void rejectsParallelImage() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "img", 0, List.of(
                        new PipelineJobSpec(null, "a", PipelineJobKind.IMAGE, "export DEST=x", null, null, null, 0),
                        new PipelineJobSpec(null, "b", PipelineJobKind.IMAGE, "export DEST=y", null, null, null, 1)
                ))
        ));
        BizException ex = assertThrows(BizException.class, () -> PipelineGraphValidator.validate(graph));
        assertEquals("镜像构建不能与其它镜像构建并行（缓存盘为 RWO）", ex.getMessage());
    }

    @Test
    void approvalPlanSplitsAroundGates() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "build", 0, List.of(
                        new PipelineJobSpec(null, "b", PipelineJobKind.BUILD, "mvn", null, null, null, 0)
                )),
                new PipelineStageSpec(null, "gate", 1, List.of(
                        new PipelineJobSpec(null, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0)
                )),
                new PipelineStageSpec(null, "deploy", 2, List.of(
                        new PipelineJobSpec(null, "d", PipelineJobKind.DEPLOY, "kubectl apply -f k8s/", null, null, null, 0)
                ))
        ));
        ApprovalPlan plan = ApprovalPlan.of(graph);
        assertFalse(plan.waitBeforeFirst());
        assertFalse(plan.waitAfterLast());
        assertEquals(2, plan.segments().size());
        assertEquals("build", plan.segments().getFirst().stages().getFirst().name());
        assertEquals("deploy", plan.segments().get(1).stages().getFirst().name());
        assertTrue(ApprovalPlan.of(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "gate", 0, List.of(
                        new PipelineJobSpec(null, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0)
                )),
                new PipelineStageSpec(null, "run", 1, List.of(
                        new PipelineJobSpec(null, "e", PipelineJobKind.CUSTOM, "echo", null, null, null, 0)
                ))
        ))).waitBeforeFirst());
    }

    @Test
    void consecutiveApprovalsStayOnTimeline() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(1L, "a1", 0, List.of(
                        new PipelineJobSpec(11L, "ok1", PipelineJobKind.APPROVAL, "", null, null, null, 0)
                )),
                new PipelineStageSpec(2L, "a2", 1, List.of(
                        new PipelineJobSpec(12L, "ok2", PipelineJobKind.APPROVAL, "", null, null, null, 0)
                )),
                new PipelineStageSpec(3L, "run", 2, List.of(
                        new PipelineJobSpec(13L, "e", PipelineJobKind.CUSTOM, "echo", null, null, null, 0)
                ))
        ));
        ApprovalPlan plan = ApprovalPlan.of(graph);
        assertTrue(plan.waitBeforeFirst());
        assertEquals(1, plan.segments().size());
        assertEquals(ApprovalPlan.Resume.WAIT, plan.afterApprovalCount(1));
        assertEquals(ApprovalPlan.Resume.SUBMIT, plan.afterApprovalCount(2));
        assertEquals(0, plan.nextSegmentIndex(2));
        assertEquals("ok2", plan.nextApprovalAfterCount(1).approvalJobName());
    }
}
