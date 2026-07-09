-- DIY 定制第一版结构。
-- 本阶段只保存方案级顺序数组，不为单颗珠子创建数据库记录。

create table customer_identity (
    id bigint primary key auto_increment,
    source varchar(32) not null,
    external_key_hash char(64) not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uk_customer_identity_source_key (source, external_key_hash),
    constraint chk_customer_identity_source check (source in ('GUEST', 'WECHAT')),
    constraint chk_customer_identity_status check (status in ('ACTIVE', 'DISABLED'))
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table diy_rule_config (
    id bigint primary key,
    min_wrist_mm decimal(6,2) not null,
    max_wrist_mm decimal(6,2) not null,
    snug_allowance_mm decimal(6,2) not null,
    standard_allowance_mm decimal(6,2) not null,
    loose_allowance_mm decimal(6,2) not null,
    tolerance_mm decimal(6,2) not null,
    max_bead_count int not null,
    config_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_diy_wrist_range check (
        min_wrist_mm > 0 and max_wrist_mm > min_wrist_mm
    ),
    constraint chk_diy_allowances check (
        snug_allowance_mm >= 0
        and standard_allowance_mm >= 0
        and loose_allowance_mm >= 0
        and tolerance_mm >= 0
    ),
    constraint chk_diy_max_beads check (
        max_bead_count between 1 and 200
    )
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table craft_fee_rule (
    id bigint primary key auto_increment,
    min_bead_count int not null,
    max_bead_count int not null,
    fee decimal(12,2) not null,
    status varchar(32) not null,
    sort_order int not null,
    rule_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_craft_count_range check (
        min_bead_count > 0 and max_bead_count >= min_bead_count
    ),
    constraint chk_craft_fee_non_negative check (fee >= 0),
    constraint chk_craft_status check (status in ('ENABLED', 'DISABLED')),
    index idx_craft_fee_match (status, min_bead_count, max_bead_count)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table cord_option (
    id bigint primary key auto_increment,
    code varchar(64) not null unique,
    name varchar(80) not null,
    color_hex char(7) not null,
    extra_fee decimal(12,2) not null,
    status varchar(32) not null,
    sort_order int not null,
    option_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_cord_fee_non_negative check (extra_fee >= 0),
    constraint chk_cord_status check (status in ('ENABLED', 'DISABLED')),
    index idx_cord_status_sort (status, sort_order)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table diy_scheme (
    id bigint primary key auto_increment,
    customer_identity_id bigint not null,
    name varchar(80) not null,
    wrist_circumference_mm decimal(6,2) not null,
    fit_type varchar(32) not null,
    cord_option_id bigint not null,
    sequence_json json not null,
    bead_count int not null,
    catalog_snapshot_json json not null,
    bead_amount decimal(12,2) null,
    craft_fee decimal(12,2) null,
    cord_fee decimal(12,2) null,
    total_amount decimal(12,2) null,
    quote_fingerprint char(64) null,
    quote_status varchar(32) not null,
    preview_asset_id bigint null,
    status varchar(32) not null,
    version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_scheme_customer
        foreign key (customer_identity_id) references customer_identity(id),
    constraint fk_scheme_cord
        foreign key (cord_option_id) references cord_option(id),
    constraint fk_scheme_preview
        foreign key (preview_asset_id) references asset_file(id),
    constraint chk_scheme_wrist_positive check (wrist_circumference_mm > 0),
    constraint chk_scheme_bead_count check (bead_count between 0 and 200),
    constraint chk_scheme_fit_type check (
        fit_type in ('SNUG', 'STANDARD', 'LOOSE')
    ),
    constraint chk_scheme_quote_status check (
        quote_status in ('NOT_QUOTED', 'VALID', 'STALE', 'INVALID')
    ),
    constraint chk_scheme_status check (status in ('ACTIVE', 'DELETED')),
    index idx_scheme_customer_status_updated (
        customer_identity_id,
        status,
        updated_at
    )
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;
