create table platform_tenant (
    id uuid primary key,
    code varchar(80) not null,
    display_name varchar(160) not null,
    status varchar(30) not null,
    created_at timestamptz not null default current_timestamp,
    constraint uk_platform_tenant_code unique (code),
    constraint ck_platform_tenant_status check (status in ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

create index idx_platform_tenant_status on platform_tenant (status);
