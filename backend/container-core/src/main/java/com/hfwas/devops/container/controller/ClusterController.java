package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.ClusterSaveDTO;
import com.hfwas.devops.container.dto.ClusterStatsVO;
import com.hfwas.devops.container.dto.ClusterUpdateDTO;
import com.hfwas.devops.container.dto.ClusterVO;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.service.SecurityHelper;
import com.hfwas.devops.container.service.cluster.ClusterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/container/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;

    @PostMapping("/page")
    public BaseResult<IPage<ClusterVO>> page(@RequestBody Map<String, Object> params) {
        int pageNo = params.get("pageNo") != null ? Integer.parseInt(params.get("pageNo").toString()) : 1;
        int pageSize = params.get("pageSize") != null ? Integer.parseInt(params.get("pageSize").toString()) : 20;
        Long tenantId = SecurityHelper.currentTenantId();
        IPage<ClusterEntity> page = clusterService.page(pageNo, pageSize, tenantId);
        IPage<ClusterVO> voPage = page.convert(this::toVO);
        return BaseResult.ok(voPage);
    }

    @GetMapping("/{id}")
    public BaseResult<ClusterVO> getById(@PathVariable Long id) {
        Long tenantId = SecurityHelper.currentTenantId();
        ClusterEntity entity = clusterService.getById(id, tenantId);
        return BaseResult.ok(toVO(entity));
    }

    @PostMapping
    public BaseResult<Long> create(@Valid @RequestBody ClusterSaveDTO dto) {
        ClusterEntity entity = new ClusterEntity();
        entity.setTenantId(SecurityHelper.currentTenantId());
        entity.setName(dto.getName());
        entity.setAlias(dto.getAlias());
        entity.setProvider(dto.getProvider());
        entity.setKubeconfig(dto.getKubeconfig());
        entity.setMode(dto.getMode() != null ? dto.getMode() : "proxy");
        if (dto.getLabels() != null) {
            try {
                entity.setLabels(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto.getLabels()));
            } catch (Exception ignored) {
                entity.setLabels("{}");
            }
        }
        return BaseResult.ok(clusterService.create(entity));
    }

    @PutMapping("/{id}")
    public BaseResult<Void> update(@PathVariable Long id, @RequestBody ClusterUpdateDTO dto) {
        Long tenantId = SecurityHelper.currentTenantId();
        ClusterEntity entity = new ClusterEntity();
        entity.setId(id);
        entity.setAlias(dto.getAlias());
        entity.setProvider(dto.getProvider());
        entity.setKubeconfig(dto.getKubeconfig());
        if (dto.getLabels() != null) {
            try {
                entity.setLabels(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto.getLabels()));
            } catch (Exception ignored) {
                entity.setLabels("{}");
            }
        }
        clusterService.update(entity, tenantId);
        return BaseResult.ok();
    }

    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        Long tenantId = SecurityHelper.currentTenantId();
        clusterService.delete(id, tenantId);
        return BaseResult.ok();
    }

    @PostMapping("/{id}/test")
    public BaseResult<Boolean> testConnection(@PathVariable Long id) {
        Long tenantId = SecurityHelper.currentTenantId();
        boolean ok = clusterService.testConnection(id, tenantId);
        return BaseResult.ok(ok);
    }

    @GetMapping("/{id}/stats")
    public BaseResult<ClusterStatsVO> stats(@PathVariable Long id) {
        Long tenantId = SecurityHelper.currentTenantId();
        ClusterService.ClusterStats s = clusterService.stats(id, tenantId);
        ClusterStatsVO vo = new ClusterStatsVO();
        vo.setNodeCount(s.nodeCount());
        vo.setPodCount(s.podCount());
        vo.setCpuTotal(s.cpuTotal());
        vo.setMemoryTotal(s.memoryTotal());
        return BaseResult.ok(vo);
    }

    // ---- internal ----

    private ClusterVO toVO(ClusterEntity entity) {
        ClusterVO vo = new ClusterVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setName(entity.getName());
        vo.setAlias(entity.getAlias());
        vo.setProvider(entity.getProvider());
        vo.setVersion(entity.getVersion());
        vo.setMode(entity.getMode());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        // kubeconfig is NEVER exposed
        return vo;
    }
}