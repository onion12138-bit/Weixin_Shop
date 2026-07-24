create table bead_visual_version (
    id bigint primary key auto_increment,
    bead_sku_id bigint not null,
    version_no int not null,
    source_type varchar(32) not null,
    shape_preset varchar(32) null,
    material_preset varchar(32) null,
    status varchar(32) not null,
    tray_asset_id bigint null,
    canvas_asset_id bigint null,
    failure_code varchar(64) null,
    failure_message varchar(500) null,
    created_by varchar(120) not null,
    published_by varchar(120) null,
    published_at timestamp null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
        on update current_timestamp,
    constraint uk_bead_visual_version unique (bead_sku_id, version_no),
    constraint fk_bead_visual_sku
        foreign key (bead_sku_id) references bead_sku(id),
    constraint fk_bead_visual_tray
        foreign key (tray_asset_id) references asset_file(id),
    constraint fk_bead_visual_canvas
        foreign key (canvas_asset_id) references asset_file(id),
    index idx_bead_visual_sku_created (bead_sku_id, created_at)
) engine = InnoDB default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci;

alter table bead_sku
    add column published_visual_version_id bigint null after cover_asset_id,
    add constraint fk_bead_published_visual
        foreign key (published_visual_version_id)
        references bead_visual_version(id);

create table diy_visual_config (
    id bigint primary key,
    canvas_background_color varchar(7) not null,
    bracelet_shadow_opacity decimal(4,3) not null,
    bead_shadow_opacity decimal(4,3) not null,
    bead_overlap_ratio decimal(4,3) not null,
    config_version bigint not null,
    updated_by varchar(120) null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
        on update current_timestamp,
    constraint chk_visual_bracelet_shadow
        check (bracelet_shadow_opacity between 0 and 0.4),
    constraint chk_visual_bead_shadow
        check (bead_shadow_opacity between 0 and 0.5),
    constraint chk_visual_overlap
        check (bead_overlap_ratio between 0 and 0.15)
) engine = InnoDB default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci;

insert into diy_visual_config (
    id, canvas_background_color, bracelet_shadow_opacity,
    bead_shadow_opacity, bead_overlap_ratio, config_version
) values (1, '#fffdfb', 0.160, 0.200, 0.060, 1);
