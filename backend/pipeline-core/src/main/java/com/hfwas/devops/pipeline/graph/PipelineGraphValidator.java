package com.hfwas.devops.pipeline.graph;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

import java.util.Comparator;
import java.util.List;

public final class PipelineGraphValidator {

    private PipelineGraphValidator() {
    }

    public static void validate(PipelineGraphSpec graph) {
        if (graph == null || graph.stages() == null || graph.stages().isEmpty()) {
            throw BizException.of(ResultCode.BAD_REQUEST, "流水线至少需要一个阶段");
        }
        List<PipelineJobSpec> jobs = graph.stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageSpec::sortOrder))
                .flatMap(stage -> stage.jobs() == null ? java.util.stream.Stream.empty() : stage.jobs().stream())
                .toList();
        long cloneCount = jobs.stream().filter(job -> job.kind() == PipelineJobKind.CLONE).count();
        if (cloneCount > 1) {
            throw BizException.of(ResultCode.BAD_REQUEST, "流水线至多一个 clone 任务");
        }
        for (PipelineStageSpec stage : graph.stages()) {
            if (stage.name() == null || stage.name().isBlank()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "阶段名称不能为空");
            }
            if (stage.jobs() == null || stage.jobs().isEmpty()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "阶段「" + stage.name() + "」至少需要一个任务");
            }
            long approvals = stage.jobs().stream().filter(job -> job.kind() == PipelineJobKind.APPROVAL).count();
            if (approvals > 0 && stage.jobs().size() != 1) {
                throw BizException.of(ResultCode.BAD_REQUEST, "审批任务必须独占一列");
            }
            long images = stage.jobs().stream().filter(job -> job.kind() == PipelineJobKind.IMAGE).count();
            if (images > 1) {
                throw BizException.of(ResultCode.BAD_REQUEST, "镜像构建不能与其它镜像构建并行（缓存盘为 RWO）");
            }
            for (PipelineJobSpec job : stage.jobs()) {
                if (job.name() == null || job.name().isBlank()) {
                    throw BizException.of(ResultCode.BAD_REQUEST, "任务名称不能为空");
                }
                if (job.kind() != null && job.kind().requiresCommand()
                        && (job.command() == null || job.command().isBlank())) {
                    throw BizException.of(ResultCode.BAD_REQUEST, "任务「" + job.name() + "」需要命令");
                }
            }
        }
    }
}
