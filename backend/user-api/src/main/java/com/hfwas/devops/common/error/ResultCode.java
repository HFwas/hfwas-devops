package com.hfwas.devops.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResultCode implements ErrorCode {

    SUCCESS(0, "成功"),

    // 10000 – common
    INTERNAL_ERROR(10001, "服务器内部错误"),
    BAD_REQUEST(10002, "请求参数错误"),
    NOT_FOUND(10003, "资源不存在"),
    DUPLICATE(10004, "数据已存在"),
    OPERATION_FAILED(10005, "操作失败"),
    IMPORT_EMPTY(10006, "导入内容不能为空"),
    SCHEMA_UNSUPPORTED(10007, "不支持的 schema 版本"),
    FILE_INVALID(10008, "文件无效"),
    FILE_READ_FAILED(10009, "读取文件失败"),
    FIELD_KEYS_INVALID(10010, "fieldKeys 格式错误"),

    // 11000 – auth
    UNAUTHORIZED(11001, "未登录或登录已过期"),
    FORBIDDEN(11002, "无权访问"),
    ADMIN_REQUIRED(11003, "需要管理员权限"),
    PLATFORM_ADMIN_REQUIRED(11004, "需要平台管理员权限"),
    TENANT_FORBIDDEN(11005, "无权访问该租户"),
    TENANT_ID_INVALID(11006, "无效的租户 ID"),
    TENANT_ID_REQUIRED(11007, "租户 ID 不能为空"),
    NOT_TENANT_MEMBER(11008, "您尚未加入该租户"),
    NOT_TENANT_MEMBER_CONTACT(11009, "您尚未加入该租户，请联系管理员"),
    TENANT_CONTEXT_MISSING(11010, "未登录或租户上下文缺失"),

    // 12000 – user
    USER_NOT_FOUND(12001, "用户不存在"),
    USER_PASSWORD_WRONG(12002, "用户名或密码错误"),
    USERNAME_PASSWORD_REQUIRED(12003, "用户名和密码不能为空"),
    USERNAME_DISPLAY_NAME_REQUIRED(12004, "用户名和显示名称不能为空"),
    USERNAME_EXISTS(12005, "用户名已存在"),
    INVALID_ROLE(12006, "无效的角色"),
    PASSWORD_REQUIRED(12007, "新建用户必须设置密码"),
    CANNOT_DELETE_SELF(12008, "不能删除当前登录用户"),
    USER_ID_REQUIRED(12009, "用户 ID 不能为空"),
    USER_SELECT_REQUIRED(12010, "请选择要加入的用户"),
    USER_NOT_MEMBER(12011, "该用户不是租户成员"),
    INVALID_TENANT_ROLE(12012, "无效的租户角色"),

    // 13000 – tenant
    TENANT_NOT_FOUND(13001, "租户不存在"),
    TENANT_DISABLED(13002, "租户已停用"),
    TENANT_CODE_EXISTS(13003, "租户编码已存在"),
    DEFAULT_TENANT_PROTECTED(13004, "默认租户不可停用"),
    DEFAULT_TENANT_DELETE_FORBIDDEN(13005, "默认租户不可删除"),
    TENANT_HAS_MEMBERS(13006, "租户下仍有成员，无法删除"),
    TENANT_HAS_PROJECTS(13007, "租户下仍有项目，无法删除"),
    TENANT_CODE_NAME_REQUIRED(13008, "租户编码和名称不能为空"),
    TENANT_CODE_FORMAT_INVALID(13009, "租户编码需以小写字母开头，仅含小写字母、数字、下划线或连字符"),

    // 14000 – session
    SESSION_NOT_FOUND(14001, "会话不存在或已下线"),

    // 15000 – message & notify
    MESSAGE_NOT_FOUND(15001, "消息不存在"),
    MESSAGE_FORBIDDEN(15002, "无权查看该消息"),
    MESSAGE_OPERATE_FORBIDDEN(15003, "无权操作该消息"),
    MESSAGE_TITLE_REQUIRED(15004, "消息标题不能为空"),
    MESSAGE_TARGET_REQUIRED(15005, "请选择发送目标"),
    MESSAGE_TARGET_TENANT_REQUIRED(15006, "请选择目标租户"),
    MESSAGE_TARGET_USER_REQUIRED(15007, "请选择目标用户"),
    MESSAGE_TARGET_TYPE_INVALID(15008, "无效的发送目标类型"),
    NOTIFY_CHANNEL_TYPE_REQUIRED(15009, "渠道类型不能为空"),
    NOTIFY_CHANNEL_NOT_FOUND(15010, "通知渠道不存在"),
    NOTIFY_CHANNEL_UNSUPPORTED(15011, "不支持的通知渠道"),
    NOTIFY_WEBHOOK_URL_REQUIRED(15012, "Webhook 地址不能为空"),
    NOTIFY_CONFIG_JSON_INVALID(15013, "配置 JSON 无效"),

    // 16000 – integration
    CONNECTOR_NAME_REQUIRED(16001, "对接名称不能为空"),
    CONNECTOR_NOT_FOUND(16002, "对接配置不存在"),
    CONNECTOR_TYPE_UNSUPPORTED(16003, "不支持的对接类型"),
    CONNECTOR_DISABLED(16004, "对接已停用，无法同步"),
    DEFAULT_TENANT_MISSING(16005, "默认租户不存在"),

    // 20000 – project
    PROJECT_NOT_FOUND(20001, "项目不存在"),
    PROJECT_ACCESS_DENIED(20002, "项目不存在或无权访问"),
    PROJECT_CODE_NAME_REQUIRED(20003, "项目编码和名称不能为空"),
    PROJECT_CODE_DUPLICATE(20004, "当前租户下项目编码已存在"),
    PROJECT_ID_REQUIRED(20005, "projectId 不能为空"),
    PROJECT_TYPE_CODE_REQUIRED(20006, "projectId 与 typeCode 不能为空"),

    // 21000 – work item
    WORK_ITEM_NOT_FOUND(21001, "事项不存在"),
    TITLE_REQUIRED(21002, "标题不能为空"),
    EXCEL_NO_DATA(21003, "Excel 中没有数据行"),
    EXPORT_ROWS_EXCEEDED(21004, "导出数据超过限制，请缩小筛选范围"),
    EXPORT_FIELDS_REQUIRED(21005, "请至少选择一个导出字段"),
    IMPORT_FIELDS_REQUIRED(21006, "请至少选择一个导入字段"),
    EXCEL_FILE_REQUIRED(21007, "请上传 Excel 文件"),
    COMMENT_EMPTY(21008, "评论内容不能为空"),
    COMMENT_NOT_FOUND(21009, "评论不存在"),
    COMMENT_FORBIDDEN(21010, "无权删除该评论"),
    COMMENT_REPLY_NOT_FOUND(21011, "回复目标不存在"),
    STATUS_TRANSITION_FORBIDDEN(21012, "状态流转不允许"),
    STATUS_NOT_FOUND(21013, "状态不存在"),
    STATUS_REQUIRED(21014, "目标状态不能为空"),
    STATUS_LIST_EMPTY(21015, "至少需要一个状态"),
    STATUS_CODE_NAME_REQUIRED(21016, "状态编码与名称不能为空"),
    STATUS_CODE_DUPLICATE(21017, "状态编码重复"),
    STATUS_NORMAL_REQUIRED(21018, "至少需要一个常规状态"),
    STATUS_INITIAL_REQUIRED(21019, "必须且只能有一个初始状态"),
    STATUS_TRANSITION_TARGET_INVALID(21020, "流转目标不存在"),
    STATUS_SELF_TRANSITION(21021, "状态不能流转到自身"),
    TYPE_CODE_UNSUPPORTED(21022, "不支持的事项类型"),
    TYPE_CODE_REQUIRED(21023, "typeCode 不能为空"),
    IMPORT_KIND_UNSUPPORTED(21024, "不支持的配置包类型"),
    IMPORT_TYPE_MISSING(21025, "导入包缺少 typeCode"),
    IMPORT_TYPES_EMPTY(21026, "导入包中没有事项类型配置"),
    EXCEL_GENERATE_FAILED(21027, "生成 Excel 失败"),
    USER_LOOKUP_FAILED(21028, "未找到用户"),
    MODULE_LOOKUP_FAILED(21029, "未找到模块"),
    NUMBER_FORMAT_INVALID(21030, "数字格式错误"),
    STATUS_LOOKUP_FAILED(21031, "未找到状态"),
    DATE_FORMAT_INVALID(21032, "日期格式错误"),

    // 22000 – field & scheme
    FIELD_CODE_NAME_REQUIRED(22001, "字段编码和名称不能为空"),
    FIELD_TYPE_REQUIRED(22002, "项目字段必须指定适用的事项类型"),
    FIELD_NOT_FOUND(22003, "字段不存在或已删除"),
    FIELD_SYSTEM_PROTECTED(22004, "系统字段不可删除"),
    FIELD_BINDING_PROTECTED(22005, "系统字段不可修改绑定"),
    FIELD_NOT_BOUND(22006, "该字段未绑定到当前事项类型"),
    FIELD_PROJECT_MISMATCH(22007, "字段不属于当前项目"),
    REMOTE_URL_REQUIRED(22008, "远程选项接口地址不能为空"),
    REMOTE_FIELD_MAPPING_REQUIRED(22009, "远程选项的值字段与显示字段不能为空"),
    STATIC_OPTIONS_REQUIRED(22010, "静态选项列表不能为空"),
    REMOTE_HTTP_METHOD_UNSUPPORTED(22011, "仅支持 GET 或 POST 请求"),
    REMOTE_OPTIONS_NOT_FOUND(22012, "未找到选项数组，请检查 dataPath 配置"),
    REMOTE_URL_PROTOCOL_INVALID(22013, "仅支持 http 或 https 协议"),
    REMOTE_URL_INVALID(22014, "无效的 URL"),
    REMOTE_URL_INTERNAL_FORBIDDEN(22015, "不允许访问内网或本地地址"),
    REMOTE_REQUEST_FAILED(22016, "远程选项加载失败"),
    REMOTE_RESPONSE_TOO_LARGE(22017, "响应体过大"),
    FIELD_UNKNOWN(22018, "未知字段"),
    OPERATOR_UNSUPPORTED(22019, "不支持的操作符"),
    SCHEME_KIND_MISMATCH(22020, "文件类型不匹配"),

    // 23000 – module
    MODULE_PROJECT_REQUIRED(23001, "项目不能为空"),
    MODULE_NAME_REQUIRED(23002, "模块名称不能为空"),
    MODULE_SELF_PARENT(23003, "上级模块不能是自身"),
    MODULE_PARENT_NOT_FOUND(23004, "上级模块不存在"),
    MODULE_CIRCULAR_PARENT(23005, "上级模块不能是当前模块的子模块"),
    MODULE_NOT_FOUND(23006, "模块不存在或已删除"),
    MODULE_HAS_CHILDREN(23007, "请先删除或移动子模块"),
    MODULE_HAS_WORK_ITEMS(23008, "模块下仍有事项，请先调整归属后再删除"),
    MODULE_NAME_DUPLICATE(23009, "同级下已存在同名模块"),
    ;

    private final int code;
    private final String message;

    public static ResultCode of(int code) {
        for (ResultCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return BAD_REQUEST;
    }
}
