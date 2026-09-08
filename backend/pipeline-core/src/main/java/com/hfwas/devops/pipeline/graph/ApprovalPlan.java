package com.hfwas.devops.pipeline.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ApprovalPlan(
        boolean waitBeforeFirst,
        boolean waitAfterLast,
        List<PipelineGraphSpec> segments,
        List<Item> timeline
) {
    public enum Kind { SEGMENT, APPROVAL }

    public enum Resume { WAIT, SUBMIT, DONE }

    public record Item(Kind kind, Integer segmentIndex, Long approvalJobId, String approvalJobName) {
    }

    public static ApprovalPlan of(PipelineGraphSpec graph) {
        List<PipelineStageSpec> stages = graph.stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageSpec::sortOrder))
                .toList();
        boolean waitBeforeFirst = !stages.isEmpty() && isApproval(stages.getFirst());
        boolean waitAfterLast = !stages.isEmpty() && isApproval(stages.getLast());
        List<PipelineGraphSpec> segments = new ArrayList<>();
        List<Item> timeline = new ArrayList<>();
        List<PipelineStageSpec> current = new ArrayList<>();
        int segmentIndex = 0;
        for (PipelineStageSpec stage : stages) {
            if (isApproval(stage)) {
                if (!current.isEmpty()) {
                    segments.add(new PipelineGraphSpec(List.copyOf(current)));
                    timeline.add(new Item(Kind.SEGMENT, segmentIndex, null, null));
                    segmentIndex++;
                    current = new ArrayList<>();
                }
                PipelineJobSpec job = stage.jobs().getFirst();
                timeline.add(new Item(Kind.APPROVAL, null, job.id(), job.name()));
            } else {
                current.add(stage);
            }
        }
        if (!current.isEmpty()) {
            segments.add(new PipelineGraphSpec(List.copyOf(current)));
            timeline.add(new Item(Kind.SEGMENT, segmentIndex, null, null));
        }
        return new ApprovalPlan(waitBeforeFirst, waitAfterLast, List.copyOf(segments), List.copyOf(timeline));
    }

    public Resume afterSegment(int segmentIndex) {
        return after(indexOfSegment(segmentIndex));
    }

    public Resume afterApprovalCount(int succeeded) {
        return after(indexOfNthApproval(succeeded));
    }

    public Integer nextSegmentIndex(int succeededApprovals) {
        return nextSegmentAfter(indexOfNthApproval(succeededApprovals));
    }

    public Integer nextSegmentAfterSegment(int segmentIndex) {
        return nextSegmentAfter(indexOfSegment(segmentIndex));
    }

    public Item nextApprovalAfterSegment(int segmentIndex) {
        return nextApproval(indexOfSegment(segmentIndex));
    }

    public Item nextApprovalAfterCount(int succeeded) {
        return nextApproval(indexOfNthApproval(succeeded));
    }

    public Item firstApproval() {
        return timeline.stream().filter(item -> item.kind() == Kind.APPROVAL).findFirst().orElse(null);
    }

    private Resume after(int timelineIndex) {
        if (timelineIndex < 0 || timelineIndex + 1 >= timeline.size()) {
            return Resume.DONE;
        }
        return timeline.get(timelineIndex + 1).kind() == Kind.APPROVAL ? Resume.WAIT : Resume.SUBMIT;
    }

    private Integer nextSegmentAfter(int timelineIndex) {
        for (int i = timelineIndex + 1; i < timeline.size(); i++) {
            Item item = timeline.get(i);
            if (item.kind() == Kind.SEGMENT) {
                return item.segmentIndex();
            }
        }
        return null;
    }

    private Item nextApproval(int timelineIndex) {
        for (int i = timelineIndex + 1; i < timeline.size(); i++) {
            Item item = timeline.get(i);
            if (item.kind() == Kind.APPROVAL) {
                return item;
            }
        }
        return null;
    }

    private int indexOfSegment(int segmentIndex) {
        for (int i = 0; i < timeline.size(); i++) {
            Item item = timeline.get(i);
            if (item.kind() == Kind.SEGMENT && Integer.valueOf(segmentIndex).equals(item.segmentIndex())) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfNthApproval(int n) {
        int seen = 0;
        for (int i = 0; i < timeline.size(); i++) {
            if (timeline.get(i).kind() == Kind.APPROVAL) {
                seen++;
                if (seen == n) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isApproval(PipelineStageSpec stage) {
        return stage.jobs() != null && stage.jobs().stream().anyMatch(job -> job.kind() == PipelineJobKind.APPROVAL);
    }
}
