package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysIdentityConnector;
import com.hfwas.devops.user.entity.SysTenant;
import com.hfwas.devops.user.integration.engine.IdentityConnectorRegistry;
import com.hfwas.devops.user.integration.model.ConnectorTestResult;
import com.hfwas.devops.user.integration.spi.IdentityConnectorHandler;
import com.hfwas.devops.user.integration.support.ConnectorConfigSupport;
import com.hfwas.devops.user.mapper.SysIdentityConnectorMapper;
import com.hfwas.devops.user.mapper.SysTenantMapper;
import com.hfwas.devops.user.model.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IdentityConnectorService {

    private final SysIdentityConnectorMapper connectorMapper;
    private final SysTenantMapper tenantMapper;
    private final IdentityConnectorRegistry connectorRegistry;
    private final ConnectorConfigSupport configSupport;

    public List<IdentityConnectorTypeVO> listTypes() {
        List<IdentityConnectorTypeVO> list = new ArrayList<>();
        for (IdentityConnectorHandler handler : connectorRegistry.all()) {
            IdentityConnectorTypeVO vo = new IdentityConnectorTypeVO();
            vo.setType(handler.type());
            vo.setLabel(handler.typeLabel());
            vo.setDescription(switch (handler.type()) {
                case "ldap" -> "从 LDAP / Active Directory 同步用户到平台账号";
                default -> "外部身份源对接";
            });
            list.add(vo);
        }
        return list;
    }

    public IPage<IdentityConnectorVO> page(IdentityConnectorPageRequest request) {
        requireAdmin();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String type = StringUtils.defaultIfBlank(request.getType(), "all");
        Page<SysIdentityConnector> page = connectorMapper.selectPage(
                new Page<>(request.resolvePageNo(), request.resolvePageSize()),
                Wrappers.<SysIdentityConnector>lambdaQuery()
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysIdentityConnector::getName, keyword)
                                .or().like(SysIdentityConnector::getType, keyword))
                        .eq(!"all".equalsIgnoreCase(type), SysIdentityConnector::getType, type)
                        .orderByDesc(SysIdentityConnector::getCreateTime));
        Map<Long, String> tenantNames = loadTenantNames(page.getRecords());
        return page.convert(c -> toVo(c, tenantNames));
    }

    public IdentityConnectorVO getById(Long id) {
        requireAdmin();
        SysIdentityConnector connector = requireConnector(id);
        Map<Long, String> tenantNames = loadTenantNames(List.of(connector));
        return toVo(connector, tenantNames);
    }

    @Transactional
    public Long save(IdentityConnectorSaveRequest request) {
        requireAdmin();
        if (StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("对接名称不能为空");
        }
        String type = StringUtils.trimToEmpty(request.getType()).toLowerCase();
        IdentityConnectorHandler handler = connectorRegistry.require(type);

        SysIdentityConnector connector;
        if (request.getId() == null) {
            connector = new SysIdentityConnector();
            connector.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        } else {
            connector = requireConnector(request.getId());
        }

        String configJson = request.getId() == null
                ? request.getConfigJson()
                : configSupport.mergeConfig(type, request.getConfigJson(), connector.getConfigJson());
        handler.validateConfig(configJson);

        connector.setName(request.getName().trim());
        connector.setType(type);
        connector.setConfigJson(configJson);
        if (request.getEnabled() != null) {
            connector.setEnabled(request.getEnabled());
        }
        connector.setDefaultTenantId(request.getDefaultTenantId());
        connector.setAutoCreateMember(request.getAutoCreateMember() == null ? 1 : request.getAutoCreateMember());

        if (connector.getDefaultTenantId() != null) {
            SysTenant tenant = tenantMapper.selectById(connector.getDefaultTenantId());
            if (tenant == null) {
                throw new IllegalArgumentException("默认租户不存在");
            }
        }

        if (request.getId() == null) {
            connectorMapper.insert(connector);
        } else {
            connectorMapper.updateById(connector);
        }
        return connector.getId();
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        connectorMapper.deleteById(id);
    }

    public ConnectorTestResult testConnection(Long id) {
        requireAdmin();
        SysIdentityConnector connector = requireConnector(id);
        return testWithConfig(connector.getType(), connector.getConfigJson());
    }

    public ConnectorTestResult testDraft(IdentityConnectorSaveRequest request) {
        requireAdmin();
        String type = StringUtils.trimToEmpty(request.getType()).toLowerCase();
        String configJson = request.getConfigJson();
        if (request.getId() != null) {
            SysIdentityConnector existing = requireConnector(request.getId());
            configJson = configSupport.mergeConfig(type, configJson, existing.getConfigJson());
        }
        return testWithConfig(type, configJson);
    }

    public String resolveConfigForSync(SysIdentityConnector connector) {
        return connector.getConfigJson();
    }

    SysIdentityConnector requireConnector(Long id) {
        SysIdentityConnector connector = connectorMapper.selectById(id);
        if (connector == null) {
            throw new IllegalArgumentException("对接配置不存在");
        }
        return connector;
    }

    private ConnectorTestResult testWithConfig(String type, String configJson) {
        IdentityConnectorHandler handler = connectorRegistry.require(type);
        return handler.testConnection(configJson);
    }

    private IdentityConnectorVO toVo(SysIdentityConnector connector, Map<Long, String> tenantNames) {
        IdentityConnectorVO vo = new IdentityConnectorVO();
        vo.setId(connector.getId());
        vo.setName(connector.getName());
        vo.setType(connector.getType());
        try {
            vo.setTypeLabel(connectorRegistry.require(connector.getType()).typeLabel());
        } catch (Exception ignored) {
            vo.setTypeLabel(connector.getType());
        }
        vo.setConfigJson(configSupport.maskSecrets(connector.getType(), connector.getConfigJson()));
        vo.setEnabled(connector.getEnabled());
        vo.setDefaultTenantId(connector.getDefaultTenantId());
        if (connector.getDefaultTenantId() != null) {
            vo.setDefaultTenantName(tenantNames.get(connector.getDefaultTenantId()));
        }
        vo.setAutoCreateMember(connector.getAutoCreateMember());
        vo.setLastSyncTime(connector.getLastSyncTime());
        vo.setLastSyncStatus(connector.getLastSyncStatus());
        vo.setLastSyncMessage(connector.getLastSyncMessage());
        vo.setLastSyncCount(connector.getLastSyncCount());
        vo.setCreateTime(connector.getCreateTime());
        return vo;
    }

    private Map<Long, String> loadTenantNames(List<SysIdentityConnector> connectors) {
        List<Long> tenantIds = connectors.stream()
                .map(SysIdentityConnector::getDefaultTenantId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery().in(SysTenant::getId, tenantIds))
                .stream()
                .collect(Collectors.toMap(SysTenant::getId, SysTenant::getName));
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }
}
