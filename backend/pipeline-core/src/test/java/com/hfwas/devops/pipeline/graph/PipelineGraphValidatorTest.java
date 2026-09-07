package com.hfwas.devops.pipeline.graph;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.toolchain.PipelineStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineGraphValidatorTest {

    @Test
    void defaultGraphIsValidAndHasCloneFirst() {
        PipelineGraphSpec graph = DefaultPipelineGraph.create(PipelineStack.JAVA_MAVEN, "21", "3.9");
        assertDoesNotThrow(() -> PipelineGraphValidator.validate(graph));
        assertEquals(3, graph.stages().size());
        assertEquals(PipelineJobKind.CLONE, graph.stages().getFirst().jobs().getFirst().kind());
        assertTrue(graph.stages().get(1).jobs().getFirst().command().contains("mvn"));
    }

    @Test
    void rejectsMissingClone() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                new PipelineStageSpec(null, "build", 0, List.of(
                        new PipelineJobSpec(null, "build", PipelineJobKind.BUILD, "mvn -B package", 0)
                ))
        ));
        BizException ex = assertThrows(BizException.class, () -> PipelineGraphValidator.validate(graph));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("流水线必须恰好有一个 clone 任务", ex.getMessage());
    }
}
