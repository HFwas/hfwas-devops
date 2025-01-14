-- auto-generated definition
create table devops_vul
(
    id             bigint auto_increment
        primary key,
    modified       varchar(28)                 null,
    published      varchar(28) charset utf8mb3 null,
    cve_id         varchar(28) charset utf8mb3 null,
    details        longtext                    null,
    serverity      longtext                    null,
    affected       longtext                    null,
    ref            longtext charset utf8mb3    null,
    databases_spec longtext                    null,
    ghsa_id        varchar(28) charset utf8mb3 null,
    ecosystem      varchar(28)                 null
)
    collate = utf8mb4_general_ci;


-- auto-generated definition
create table devops_vul_cwe
(
    id          bigint auto_increment
        primary key,
    name        varchar(128) null,
    description longtext     null,
    type        int          null
);


-- auto-generated definition
create table devops_vul_dependency
(
    id              bigint auto_increment
        primary key,
    company         varchar(28) null,
    dependency_name varchar(28) null,
    version         varchar(28) null,
    git_id          bigint      not null
)
    collate = utf8mb4_general_ci;

