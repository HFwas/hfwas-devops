package com.hfwas.devops.common.core.exception;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps legacy {@link IllegalArgumentException} messages to {@link ResultCode}.
 * Prefer throwing {@link BizException} directly in new code.
 */
public final class LegacyErrorCodeResolver {

    private static final Map<String, ResultCode> EXACT = new LinkedHashMap<>();
    private static final List<PrefixRule> PREFIXES = List.of(
            new PrefixRule("用户不存在或已禁用: ", ResultCode.USER_NOT_FOUND),
            new PrefixRule("不允许从「", ResultCode.STATUS_TRANSITION_FORBIDDEN),
            new PrefixRule("当前状态不存在: ", ResultCode.STATUS_NOT_FOUND),
            new PrefixRule("目标状态不存在: ", ResultCode.STATUS_NOT_FOUND),
            new PrefixRule("状态编码重复: ", ResultCode.STATUS_CODE_DUPLICATE),
            new PrefixRule("流转目标不存在: ", ResultCode.STATUS_TRANSITION_TARGET_INVALID),
            new PrefixRule("状态不能流转到自身: ", ResultCode.STATUS_SELF_TRANSITION),
            new PrefixRule("不支持的配置包类型: ", ResultCode.IMPORT_KIND_UNSUPPORTED),
            new PrefixRule("不支持的 schema 版本: ", ResultCode.SCHEMA_UNSUPPORTED),
            new PrefixRule("不支持的事项类型: ", ResultCode.TYPE_CODE_UNSUPPORTED),
            new PrefixRule("不支持的通知渠道: ", ResultCode.NOTIFY_CHANNEL_UNSUPPORTED),
            new PrefixRule("不支持的对接类型: ", ResultCode.CONNECTOR_TYPE_UNSUPPORTED),
            new PrefixRule("文件类型不匹配，期望 ", ResultCode.SCHEME_KIND_MISMATCH),
            new PrefixRule("Unknown custom field: ", ResultCode.FIELD_UNKNOWN),
            new PrefixRule("Unknown system field: ", ResultCode.FIELD_UNKNOWN),
            new PrefixRule("Unsupported operator: ", ResultCode.OPERATOR_UNSUPPORTED),
            new PrefixRule("Unsupported operator for custom field: ", ResultCode.OPERATOR_UNSUPPORTED),
            new PrefixRule("未找到用户: ", ResultCode.USER_LOOKUP_FAILED),
            new PrefixRule("未找到模块: ", ResultCode.MODULE_LOOKUP_FAILED),
            new PrefixRule("未找到状态: ", ResultCode.STATUS_LOOKUP_FAILED),
            new PrefixRule("数字格式错误: ", ResultCode.NUMBER_FORMAT_INVALID),
            new PrefixRule("日期格式错误: ", ResultCode.DATE_FORMAT_INVALID),
            new PrefixRule("导出数据超过 ", ResultCode.EXPORT_ROWS_EXCEEDED),
            new PrefixRule("该模块下仍有 ", ResultCode.MODULE_HAS_WORK_ITEMS),
            new PrefixRule("HTTP ", ResultCode.REMOTE_REQUEST_FAILED)
    );
    private static final Pattern FIELD_REQUIRED = Pattern.compile(".+ 不能为空$");

    static {
        map("未登录或登录已过期", ResultCode.UNAUTHORIZED);
        map("无权访问", ResultCode.FORBIDDEN);
        map("需要管理员权限", ResultCode.ADMIN_REQUIRED);
        map("需要平台管理员权限", ResultCode.PLATFORM_ADMIN_REQUIRED);
        map("无权访问该租户", ResultCode.TENANT_FORBIDDEN);
        map("无效的租户 ID", ResultCode.TENANT_ID_INVALID);
        map("租户 ID 不能为空", ResultCode.TENANT_ID_REQUIRED);
        map("您尚未加入该租户", ResultCode.NOT_TENANT_MEMBER);
        map("您尚未加入该租户，请联系管理员", ResultCode.NOT_TENANT_MEMBER_CONTACT);
        map("未登录或租户上下文缺失", ResultCode.TENANT_CONTEXT_MISSING);

        map("用户不存在", ResultCode.USER_NOT_FOUND);
        map("用户名或密码错误", ResultCode.USER_PASSWORD_WRONG);
        map("用户名和密码不能为空", ResultCode.USERNAME_PASSWORD_REQUIRED);
        map("用户名和显示名称不能为空", ResultCode.USERNAME_DISPLAY_NAME_REQUIRED);
        map("用户名已存在", ResultCode.USERNAME_EXISTS);
        map("无效的角色", ResultCode.INVALID_ROLE);
        map("新建用户必须设置密码", ResultCode.PASSWORD_REQUIRED);
        map("不能删除当前登录用户", ResultCode.CANNOT_DELETE_SELF);
        map("用户 ID 不能为空", ResultCode.USER_ID_REQUIRED);
        map("请选择要加入的用户", ResultCode.USER_SELECT_REQUIRED);
        map("该用户不是租户成员", ResultCode.USER_NOT_MEMBER);
        map("无效的租户角色", ResultCode.INVALID_TENANT_ROLE);

        map("租户不存在", ResultCode.TENANT_NOT_FOUND);
        map("租户已停用", ResultCode.TENANT_DISABLED);
        map("租户编码已存在", ResultCode.TENANT_CODE_EXISTS);
        map("默认租户不可停用", ResultCode.DEFAULT_TENANT_PROTECTED);
        map("默认租户不可删除", ResultCode.DEFAULT_TENANT_DELETE_FORBIDDEN);
        map("租户下仍有成员，无法删除", ResultCode.TENANT_HAS_MEMBERS);
        map("租户下仍有项目，无法删除", ResultCode.TENANT_HAS_PROJECTS);
        map("租户编码和名称不能为空", ResultCode.TENANT_CODE_NAME_REQUIRED);
        map("租户编码需以小写字母开头，仅含小写字母、数字、下划线或连字符", ResultCode.TENANT_CODE_FORMAT_INVALID);

        map("会话不存在或已下线", ResultCode.SESSION_NOT_FOUND);

        map("消息不存在", ResultCode.MESSAGE_NOT_FOUND);
        map("无权查看该消息", ResultCode.MESSAGE_FORBIDDEN);
        map("无权操作该消息", ResultCode.MESSAGE_OPERATE_FORBIDDEN);
        map("消息标题不能为空", ResultCode.MESSAGE_TITLE_REQUIRED);
        map("请选择发送目标", ResultCode.MESSAGE_TARGET_REQUIRED);
        map("请选择目标租户", ResultCode.MESSAGE_TARGET_TENANT_REQUIRED);
        map("请选择目标用户", ResultCode.MESSAGE_TARGET_USER_REQUIRED);
        map("无效的发送目标类型", ResultCode.MESSAGE_TARGET_TYPE_INVALID);
        map("渠道类型不能为空", ResultCode.NOTIFY_CHANNEL_TYPE_REQUIRED);
        map("通知渠道不存在", ResultCode.NOTIFY_CHANNEL_NOT_FOUND);
        map("Webhook 地址不能为空", ResultCode.NOTIFY_WEBHOOK_URL_REQUIRED);
        map("配置 JSON 无效", ResultCode.NOTIFY_CONFIG_JSON_INVALID);

        map("对接名称不能为空", ResultCode.CONNECTOR_NAME_REQUIRED);
        map("对接配置不存在", ResultCode.CONNECTOR_NOT_FOUND);
        map("对接已停用，无法同步", ResultCode.CONNECTOR_DISABLED);
        map("默认租户不存在", ResultCode.DEFAULT_TENANT_MISSING);

        map("项目不存在", ResultCode.PROJECT_NOT_FOUND);
        map("项目不存在或无权访问", ResultCode.PROJECT_ACCESS_DENIED);
        map("项目编码和名称不能为空", ResultCode.PROJECT_CODE_NAME_REQUIRED);
        map("当前租户下项目编码已存在", ResultCode.PROJECT_CODE_DUPLICATE);
        map("projectId 不能为空", ResultCode.PROJECT_ID_REQUIRED);
        map("projectId 与 typeCode 不能为空", ResultCode.PROJECT_TYPE_CODE_REQUIRED);

        map("事项不存在", ResultCode.WORK_ITEM_NOT_FOUND);
        map("标题不能为空", ResultCode.TITLE_REQUIRED);
        map("Excel 中没有数据行", ResultCode.EXCEL_NO_DATA);
        map("请至少选择一个导出字段", ResultCode.EXPORT_FIELDS_REQUIRED);
        map("请至少选择一个导入字段", ResultCode.IMPORT_FIELDS_REQUIRED);
        map("请上传 Excel 文件", ResultCode.EXCEL_FILE_REQUIRED);
        map("导入内容不能为空", ResultCode.IMPORT_EMPTY);
        map("评论内容不能为空", ResultCode.COMMENT_EMPTY);
        map("评论不存在", ResultCode.COMMENT_NOT_FOUND);
        map("无权删除该评论", ResultCode.COMMENT_FORBIDDEN);
        map("回复目标不存在", ResultCode.COMMENT_REPLY_NOT_FOUND);
        map("目标状态不能为空", ResultCode.STATUS_REQUIRED);
        map("至少需要一个状态", ResultCode.STATUS_LIST_EMPTY);
        map("状态编码与名称不能为空", ResultCode.STATUS_CODE_NAME_REQUIRED);
        map("至少需要一个常规状态", ResultCode.STATUS_NORMAL_REQUIRED);
        map("必须且只能有一个初始状态", ResultCode.STATUS_INITIAL_REQUIRED);
        map("typeCode 不能为空", ResultCode.TYPE_CODE_REQUIRED);
        map("导入包缺少 typeCode", ResultCode.IMPORT_TYPE_MISSING);
        map("导入包中没有事项类型配置", ResultCode.IMPORT_TYPES_EMPTY);
        map("读取文件失败", ResultCode.FILE_READ_FAILED);
        map("fieldKeys 格式错误", ResultCode.FIELD_KEYS_INVALID);

        map("字段编码和名称不能为空", ResultCode.FIELD_CODE_NAME_REQUIRED);
        map("项目字段必须指定适用的事项类型", ResultCode.FIELD_TYPE_REQUIRED);
        map("字段不存在或已删除", ResultCode.FIELD_NOT_FOUND);
        map("系统字段不可删除", ResultCode.FIELD_SYSTEM_PROTECTED);
        map("系统字段不可修改绑定", ResultCode.FIELD_BINDING_PROTECTED);
        map("该字段未绑定到当前事项类型", ResultCode.FIELD_NOT_BOUND);
        map("字段不属于当前项目", ResultCode.FIELD_PROJECT_MISMATCH);
        map("远程选项接口地址不能为空", ResultCode.REMOTE_URL_REQUIRED);
        map("远程选项的值字段与显示字段不能为空", ResultCode.REMOTE_FIELD_MAPPING_REQUIRED);
        map("静态选项列表不能为空", ResultCode.STATIC_OPTIONS_REQUIRED);
        map("仅支持 GET 或 POST 请求", ResultCode.REMOTE_HTTP_METHOD_UNSUPPORTED);
        map("未找到选项数组，请检查 dataPath 配置", ResultCode.REMOTE_OPTIONS_NOT_FOUND);
        map("仅支持 http 或 https 协议", ResultCode.REMOTE_URL_PROTOCOL_INVALID);
        map("无效的 URL", ResultCode.REMOTE_URL_INVALID);
        map("不允许访问内网或本地地址", ResultCode.REMOTE_URL_INTERNAL_FORBIDDEN);

        map("项目不能为空", ResultCode.MODULE_PROJECT_REQUIRED);
        map("模块名称不能为空", ResultCode.MODULE_NAME_REQUIRED);
        map("上级模块不能是自身", ResultCode.MODULE_SELF_PARENT);
        map("上级模块不存在", ResultCode.MODULE_PARENT_NOT_FOUND);
        map("上级模块不能是当前模块的子模块", ResultCode.MODULE_CIRCULAR_PARENT);
        map("模块不存在或已删除", ResultCode.MODULE_NOT_FOUND);
        map("请先删除或移动子模块", ResultCode.MODULE_HAS_CHILDREN);
        map("同级下已存在同名模块", ResultCode.MODULE_NAME_DUPLICATE);
    }

    private LegacyErrorCodeResolver() {
    }

    public static BizException resolve(IllegalArgumentException ex) {
        return resolve(ex.getMessage());
    }

    public static BizException resolve(String message) {
        if (message == null || message.isBlank()) {
            return BizException.of(ResultCode.BAD_REQUEST);
        }
        ResultCode code = EXACT.get(message);
        if (code == null) {
            for (PrefixRule rule : PREFIXES) {
                if (message.startsWith(rule.prefix)) {
                    code = rule.code;
                    break;
                }
            }
        }
        if (code == null && FIELD_REQUIRED.matcher(message).matches()) {
            code = ResultCode.BAD_REQUEST;
        }
        if (code == null) {
            code = ResultCode.BAD_REQUEST;
        }
        return new BizException(code, message);
    }

    public static BizException resolve(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null) {
            if (message.startsWith("生成 Excel 失败")) {
                return new BizException(ResultCode.EXCEL_GENERATE_FAILED, message);
            }
            if (message.startsWith("HTTP ")) {
                return new BizException(ResultCode.REMOTE_REQUEST_FAILED, message);
            }
            if (message.startsWith("响应体过大")) {
                return new BizException(ResultCode.REMOTE_RESPONSE_TOO_LARGE, message);
            }
            if (message.contains("远程选项加载失败")) {
                return new BizException(ResultCode.REMOTE_REQUEST_FAILED, message);
            }
            if (message.contains("TenantAccessValidator is not configured")) {
                return new BizException(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.getMessage());
            }
        }
        return new BizException(ResultCode.INTERNAL_ERROR,
                message != null ? message : ResultCode.INTERNAL_ERROR.getMessage());
    }

    private static void map(String message, ResultCode code) {
        EXACT.put(message, code);
    }

    private record PrefixRule(String prefix, ResultCode code) {
    }
}
