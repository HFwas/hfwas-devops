package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.crypto.CredentialCipher;
import com.hfwas.devops.pipeline.dto.CredentialSaveDTO;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.entity.PipelineCredentialEntity;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.mapper.PipelineCredentialMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PipelineCredentialService {

    private final PipelineCredentialMapper mapper;
    private final PipelineMapper pipelineMapper;
    private final CredentialCipher cipher;
    private final CurrentUserAccessor currentUserAccessor;

    public PipelineCredentialService(
            PipelineCredentialMapper mapper,
            PipelineMapper pipelineMapper,
            CredentialCipher cipher,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.mapper = mapper;
        this.pipelineMapper = pipelineMapper;
        this.cipher = cipher;
        this.currentUserAccessor = currentUserAccessor;
    }

    public List<CredentialVO> list() {
        Long tenantId = requireTenant();
        return mapper.selectList(new LambdaQueryWrapper<PipelineCredentialEntity>()
                        .eq(PipelineCredentialEntity::getTenantId, tenantId)
                        .orderByDesc(PipelineCredentialEntity::getUpdateTime))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public CredentialVO get(Long id) {
        return toVo(requireOwned(id));
    }

    @Transactional
    public Long save(CredentialSaveDTO dto) {
        Long tenantId = requireTenant();
        PipelineCredentialEntity row = dto.getId() == null ? new PipelineCredentialEntity() : requireOwned(dto.getId());
        if (!StringUtils.hasText(dto.getName())) {
            throw BizException.of(ResultCode.BAD_REQUEST, "凭证名称不能为空");
        }
        if (!StringUtils.hasText(dto.getKind())) {
            throw BizException.of(ResultCode.BAD_REQUEST, "凭证类型不能为空");
        }
        String kind = dto.getKind().trim().toUpperCase();
        if (!"PASSWORD".equals(kind) && !"TOKEN".equals(kind)) {
            throw BizException.of(ResultCode.BAD_REQUEST, "凭证类型必须是 PASSWORD 或 TOKEN");
        }
        row.setTenantId(tenantId);
        row.setName(dto.getName().trim());
        row.setKind(kind);
        String username = dto.getUsername();
        if (!StringUtils.hasText(username) && "TOKEN".equals(kind)) {
            username = "x-access-token";
        }
        if ("PASSWORD".equals(kind) && !StringUtils.hasText(username)) {
            throw BizException.of(ResultCode.BAD_REQUEST, "账号密码凭证需要用户名");
        }
        row.setUsername(username);
        if (dto.getId() == null || StringUtils.hasText(dto.getSecret())) {
            if (!StringUtils.hasText(dto.getSecret())) {
                throw BizException.of(ResultCode.BAD_REQUEST, "密钥不能为空");
            }
            row.setSecretEnc(cipher.encrypt(dto.getSecret()));
        }
        row.setUpdateBy(currentUserAccessor.currentUserId());
        if (row.getId() == null) {
            row.setCreateBy(currentUserAccessor.currentUserId());
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        return row.getId();
    }

    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        Long used = pipelineMapper.selectCount(new LambdaQueryWrapper<PipelineEntity>()
                .eq(PipelineEntity::getCredentialId, id));
        if (used != null && used > 0) {
            throw BizException.of(ResultCode.BAD_REQUEST, "凭证仍被流水线引用，无法删除");
        }
        mapper.deleteById(id);
    }

    public String decryptSecret(Long id) {
        return cipher.decrypt(requireOwned(id).getSecretEnc());
    }

    PipelineCredentialEntity requireOwned(Long id) {
        PipelineCredentialEntity row = mapper.selectById(id);
        if (row == null || !requireTenant().equals(row.getTenantId())) {
            throw BizException.of(ResultCode.NOT_FOUND, "凭证不存在");
        }
        return row;
    }

    private CredentialVO toVo(PipelineCredentialEntity row) {
        CredentialVO vo = new CredentialVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setKind(row.getKind());
        vo.setUsername(row.getUsername());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    private Long requireTenant() {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (tenantId == null) {
            throw BizException.of(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }
}
