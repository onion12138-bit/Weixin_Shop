-- 商品目录第一版结构。
-- 金额统一使用 decimal(12,2)，状态使用稳定英文码，避免客户端依赖数据库枚举。

create table asset_file (
    id bigint primary key auto_increment,
    storage_key varchar(255) not null unique,
    original_name varchar(255) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null,
    public_url varchar(500) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_asset_size_non_negative check (size_bytes >= 0)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table category (
    id bigint primary key auto_increment,
    name varchar(80) not null,
    slug varchar(80) not null unique,
    sort_order int not null default 0,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    index idx_category_status_sort (status, sort_order)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table bead_sku (
    id bigint primary key auto_increment,
    category_id bigint not null,
    sku_code varchar(64) not null unique,
    name varchar(120) not null,
    material varchar(80) not null,
    color varchar(80) not null,
    diameter_mm decimal(5,2) not null,
    sale_price decimal(12,2) not null,
    cost_price decimal(12,2) not null,
    stock_alert_threshold int not null default 0,
    cover_asset_id bigint null,
    tags_json json not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_bead_category
        foreign key (category_id) references category(id),
    constraint fk_bead_cover
        foreign key (cover_asset_id) references asset_file(id),
    constraint chk_bead_diameter_positive check (diameter_mm > 0),
    constraint chk_bead_sale_price_non_negative check (sale_price >= 0),
    constraint chk_bead_cost_price_non_negative check (cost_price >= 0),
    constraint chk_bead_threshold_non_negative check (stock_alert_threshold >= 0),
    index idx_bead_public_list (status, category_id, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table product_package (
    id bigint primary key auto_increment,
    package_code varchar(64) not null unique,
    name varchar(120) not null,
    description varchar(1000) not null,
    sale_price decimal(12,2) not null,
    cover_asset_id bigint null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_package_cover
        foreign key (cover_asset_id) references asset_file(id),
    constraint chk_package_sale_price_non_negative check (sale_price >= 0),
    index idx_package_public_list (status, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table package_item (
    id bigint primary key auto_increment,
    package_id bigint not null,
    bead_sku_id bigint not null,
    quantity int not null,
    sort_order int not null,
    constraint fk_item_package
        foreign key (package_id) references product_package(id)
        on delete cascade,
    constraint fk_item_bead
        foreign key (bead_sku_id) references bead_sku(id),
    constraint chk_package_item_quantity_positive check (quantity > 0),
    unique key uk_package_sort (package_id, sort_order),
    index idx_package_item_bead (bead_sku_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;
