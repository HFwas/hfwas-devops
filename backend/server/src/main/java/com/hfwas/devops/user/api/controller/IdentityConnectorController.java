package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.integration.model.ConnectorSyncResult;
import com.hfwas.devops.user.integration.model.ConnectorTestResult;
import com.hfwas.devops.user.model.*;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import com.hfwas.devops.user.service.IdentityConnectorService;
import com.hfwas.devops.user.service.IdentityConnectorSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/integrations")
@RequiredArgsConstructor
public class IdentityConnectorController {

    private final IdentityConnectorService connectorService;
    private final IdentityConnectorSyncService syncService;

    @GetMapping("/types")
    public BaseResult<List<IdentityConnectorTypeVO>> types() {
        return BaseResult.ok(connectorService.listTypes());
    }

    @PostMapping("/page")
    public BaseResult<IPage<IdentityConnectorVO>> page(@RequestBody IdentityConnectorPageRequest request) {
        return BaseResult.ok(connectorService.page(request));
    }

    @GetMapping("/{id}")
    public BaseResult<IdentityConnectorVO> getById(@PathVariable Long id) {
        return BaseResult.ok(connectorService.getById(id));
    }

    @OperLog(module = "user", action = "save", bizType = "identity_connector", summary = "保存三方对接", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody IdentityConnectorSaveRequest request) {
        return BaseResult.ok(connectorService.save(request));
    }

    @OperLog(module = "user", action = "delete", bizType = "identity_connector", summary = "删除三方对接", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        connectorService.delete(id);
        return BaseResult.ok();
    }

    @PostMapping("/test-connection")
    public BaseResult<ConnectorTestResult> testConnection(@RequestBody IdentityConnectorSaveRequest request) {
        if (request.getId() != null && request.getConfigJson() == null) {
            return BaseResult.ok(connectorService.testConnection(request.getId()));
        }
        return BaseResult.ok(connectorService.testDraft(request));
    }

    @OperLog(module = "user", action = "save", bizType = "identity_connector", summary = "同步外部用户", bizId = "#id")
    @PostMapping("/sync")
    public BaseResult<ConnectorSyncResult> sync(@RequestParam("id") Long id) {
        return BaseResult.ok(syncService.sync(id));
    }
}
