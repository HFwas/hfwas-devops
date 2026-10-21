package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchOperationResultVO {
    private String batchId;
    private boolean dryRun;
    private long total;
    private long success;
    private long skipped;
    private long failed;
    private List<BatchOperationItemVO> items = new ArrayList<>();

    public void addItem(BatchOperationItemVO item) {
        items.add(item);
        total++;
        switch (item.getOutcome()) {
            case BatchOperationItemVO.SUCCESS -> success++;
            case BatchOperationItemVO.SKIPPED -> skipped++;
            default -> failed++;
        }
    }
}
