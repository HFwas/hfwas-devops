-- auto-generated definition
create table devops_vul
(
    id            bigint auto_increment
        primary key,
    modified      varchar(28)  null,
    published     varchar(28)  null,
    cve_id        varchar(28)  null,
    details       longtext     null,
    serverity     longtext     null,
    ref           longtext     null,
    ghsa_id       varchar(28)  null,
    ecosystem     varchar(28)  null,
    packages      varchar(128) null,
    introduced    varchar(56)  null,
    fixed         varchar(56)  null,
    cvss_v3_score varchar(128) null,
    cwe_ids       varchar(56)  null
)
    charset = utf8mb4_general_ci;

-- auto-generated definition
create table devops_vul_code_version
(
    id               bigint auto_increment
        primary key,
    depen_version_id bigint not null,
    code_id          bigint not null
)
    charset = utf8mb4_general_ci;;

-- auto-generated definition
create table devops_vul_cwe
(
    id          bigint auto_increment
        primary key,
    name        varchar(128) null,
    description longtext     null,
    type        int          null
)
    charset = utf8mb4_general_ci;;

-- auto-generated definition
create table devops_vul_dependency
(
    id              bigint auto_increment
        primary key,
    company         varchar(56) null,
    dependency_name varchar(28) null,
    type            smallint    not null comment '语言类型，比如Java，npm，python'
)
    charset = utf8mb4_general_ci;

-- auto-generated definition
create table devops_vul_dependency_version
(
    id       bigint auto_increment
        primary key,
    depen_id bigint      not null,
    version  varchar(56) null
)
    charset = utf8mb4_general_ci;



create table devops_sync_log
(
    id       bigint auto_increment
        primary key,
    type smallint      not null,
    status  varchar(56) null,
    createTime  varchar(56) null,
    updateTime  varchar(56) null,
    success_num  int8 null,
    success_data  longtext null,
    error_num  int8 null,
    error_data  longtext null
)
    charset = utf8mb4_general_ci;