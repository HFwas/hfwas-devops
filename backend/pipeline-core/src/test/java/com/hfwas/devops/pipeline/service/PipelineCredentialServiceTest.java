package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.crypto.CredentialCipher;
import com.hfwas.devops.pipeline.dto.CredentialSaveDTO;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.dto.PipelinePageQuery;
import com.hfwas.devops.pipeline.entity.PipelineCredentialEntity;
import com.hfwas.devops.pipeline.mapper.PipelineCredentialMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-063..076、PIPE-153..156：凭证规则、加密与隔离。
 */
@ExtendWith(MockitoExtension.class)
class PipelineCredentialServiceTest {

    @Mock
    private PipelineCredentialMapper mapper;
    @Mock
    private PipelineMapper pipelineMapper;
    @Mock
    private CredentialCipher cipher;
    @Mock
    private CurrentUserAccessor currentUserAccessor;

    private PipelineCredentialService service;

    @BeforeEach
    void setUp() {
        service = new PipelineCredentialService(mapper, pipelineMapper, cipher, currentUserAccessor);
        lenient().when(currentUserAccessor.currentTenantId()).thenReturn(9L);
        lenient().when(currentUserAccessor.currentUserId()).thenReturn(7L);
        lenient().when(cipher.encrypt(any())).thenReturn("enc");
        lenient().when(cipher.decrypt("enc")).thenReturn("plain");
    }

    @Test
    @DisplayName("PIPE-063: 列表只返回当前租户凭证且不含密钥")
    void listReturnsTenantCredentialsWithoutSecret() {
        when(mapper.selectList(any())).thenReturn(List.of(credential(1L, "git", "PASSWORD")));

        List<CredentialVO> rows = service.list();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getName()).isEqualTo("git");
        assertThat(rows.getFirst()).hasNoNullFieldsOrPropertiesExcept("updateTime");
        // CredentialVO 没有 secret 字段，编译期即保证不会泄露
    }

    @Test
    @DisplayName("PIPE-076: 分页使用默认分页并支持关键词")
    void pageUsesDefaults() {
        Page<PipelineCredentialEntity> result = new Page<>(1, 10);
        result.setRecords(List.of(credential(1L, "git", "PASSWORD")));
        when(mapper.selectPage(any(), any())).thenReturn(result);

        var page = service.page(new PipelinePageQuery());

        assertThat(page.getRecords()).hasSize(1);
        verify(mapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("PIPE-063: 创建 PASSWORD 凭证加密保存并回填 id")
    void createPasswordEncryptsSecret() {
        when(mapper.insert(any(PipelineCredentialEntity.class))).thenAnswer(inv -> {
            inv.<PipelineCredentialEntity>getArgument(0).setId(5L);
            return 1;
        });
        CredentialSaveDTO dto = dto("git", "password", "ada", "s3cr3t");

        assertThat(service.save(dto)).isEqualTo(5L);

        ArgumentCaptor<PipelineCredentialEntity> captor = ArgumentCaptor.forClass(PipelineCredentialEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(9L);
        assertThat(captor.getValue().getKind()).isEqualTo("PASSWORD");
        assertThat(captor.getValue().getUsername()).isEqualTo("ada");
        assertThat(captor.getValue().getSecretEnc()).isEqualTo("enc");
        assertThat(captor.getValue().getCreateBy()).isEqualTo(7L);
    }

    @Test
    @DisplayName("PIPE-064: TOKEN 未填用户名时默认 x-access-token")
    void createTokenDefaultsUsername() {
        CredentialSaveDTO dto = dto("token", "token", null, "t");

        service.save(dto);

        ArgumentCaptor<PipelineCredentialEntity> captor = ArgumentCaptor.forClass(PipelineCredentialEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("x-access-token");
    }

    @Test
    @DisplayName("PIPE-065: KUBECONFIG 可不填用户名")
    void createKubeconfigWithoutUsername() {
        CredentialSaveDTO dto = dto("kube", "kubeconfig", null, "apiVersion: v1");

        service.save(dto);

        ArgumentCaptor<PipelineCredentialEntity> captor = ArgumentCaptor.forClass(PipelineCredentialEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("KUBECONFIG");
        assertThat(captor.getValue().getUsername()).isNull();
    }

    @Test
    @DisplayName("PIPE-063: 名称/类型为空返回 10002")
    void createRejectsBlankNameAndKind() {
        BizException noName = assertThrows(BizException.class, () -> service.save(dto(" ", "TOKEN", null, "t")));
        assertThat(noName.getMessage()).contains("凭证名称不能为空");

        BizException noKind = assertThrows(BizException.class, () -> service.save(dto("token", " ", null, "t")));
        assertThat(noKind.getMessage()).contains("凭证类型不能为空");
    }

    @Test
    @DisplayName("PIPE-066: 非法凭证类型返回 10002")
    void createRejectsUnsupportedKind() {
        BizException ex = assertThrows(BizException.class, () -> service.save(dto("ssh", "SSH", "root", "x")));

        assertThat(ex.getMessage()).contains("PASSWORD、TOKEN 或 KUBECONFIG");
        verify(mapper, never()).insert(any(PipelineCredentialEntity.class));
    }

    @Test
    @DisplayName("PIPE-067: PASSWORD 缺少用户名返回 10002")
    void createPasswordRequiresUsername() {
        BizException ex = assertThrows(BizException.class,
                () -> service.save(dto("git", "PASSWORD", null, "s3cr3t")));

        assertThat(ex.getMessage()).contains("需要用户名");
    }

    @Test
    @DisplayName("PIPE-068: 创建时密钥为空返回 10002")
    void createRequiresSecret() {
        BizException ex = assertThrows(BizException.class,
                () -> service.save(dto("token", "TOKEN", null, " ")));

        assertThat(ex.getMessage()).contains("密钥不能为空");
    }

    @Test
    @DisplayName("PIPE-069: 更新时密钥留空保留原密文")
    void updateWithBlankSecretKeepsCiphertext() {
        PipelineCredentialEntity existing = credential(1L, "git", "PASSWORD");
        existing.setSecretEnc("old-enc");
        when(mapper.selectById(1L)).thenReturn(existing);
        CredentialSaveDTO dto = dto("git-new", "PASSWORD", "ada", null);
        dto.setId(1L);

        service.save(dto);

        ArgumentCaptor<PipelineCredentialEntity> captor = ArgumentCaptor.forClass(PipelineCredentialEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getSecretEnc()).isEqualTo("old-enc");
        assertThat(captor.getValue().getName()).isEqualTo("git-new");
        verify(cipher, never()).encrypt(any());
    }

    @Test
    @DisplayName("PIPE-070: 更新时传新密钥重新加密")
    void updateWithSecretReencrypts() {
        when(mapper.selectById(1L)).thenReturn(credential(1L, "git", "PASSWORD"));
        when(cipher.encrypt("new-secret")).thenReturn("new-enc");
        CredentialSaveDTO dto = dto("git", "PASSWORD", "ada", "new-secret");
        dto.setId(1L);

        service.save(dto);

        ArgumentCaptor<PipelineCredentialEntity> captor = ArgumentCaptor.forClass(PipelineCredentialEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getSecretEnc()).isEqualTo("new-enc");
    }

    @Test
    @DisplayName("PIPE-071: 详情不返回密钥，decryptSecret 仅供内部使用")
    void getAndDecryptNeverExposeSecretInVo() {
        PipelineCredentialEntity row = credential(1L, "git", "PASSWORD");
        row.setSecretEnc("enc");
        when(mapper.selectById(1L)).thenReturn(row);

        CredentialVO vo = service.get(1L);
        String plain = service.decryptSecret(1L);

        assertThat(vo.getName()).isEqualTo("git");
        assertThat(plain).isEqualTo("plain");
        assertThat(vo.toString()).doesNotContain("enc");
    }

    @Test
    @DisplayName("PIPE-073: 被流水线引用时禁止删除")
    void deleteRejectsReferencedCredential() {
        when(mapper.selectById(1L)).thenReturn(credential(1L, "git", "PASSWORD"));
        when(pipelineMapper.selectCount(any())).thenReturn(2L);

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L));

        assertThat(ex.getMessage()).contains("仍被流水线引用");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("PIPE-074: 未被引用时逻辑删除")
    void deleteRemovesUnreferencedCredential() {
        when(mapper.selectById(1L)).thenReturn(credential(1L, "git", "PASSWORD"));
        when(pipelineMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("PIPE-153: 跨租户访问返回 10003")
    void getRejectsOtherTenant() {
        PipelineCredentialEntity row = credential(1L, "git", "PASSWORD");
        row.setTenantId(8L);
        when(mapper.selectById(1L)).thenReturn(row);

        BizException ex = assertThrows(BizException.class, () -> service.get(1L));

        assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("PIPE-153: 缺少租户上下文返回 11010")
    void listRejectsMissingTenantContext() {
        when(currentUserAccessor.currentTenantId()).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.list());

        assertThat(ex.getCode()).isEqualTo(ResultCode.TENANT_CONTEXT_MISSING.getCode());
    }

    private static PipelineCredentialEntity credential(Long id, String name, String kind) {
        PipelineCredentialEntity row = new PipelineCredentialEntity();
        row.setId(id);
        row.setTenantId(9L);
        row.setName(name);
        row.setKind(kind);
        row.setUsername("ada");
        row.setUpdateTime(LocalDateTime.now());
        return row;
    }

    private static CredentialSaveDTO dto(String name, String kind, String username, String secret) {
        CredentialSaveDTO dto = new CredentialSaveDTO();
        dto.setName(name);
        dto.setKind(kind);
        dto.setUsername(username);
        dto.setSecret(secret);
        return dto;
    }
}
