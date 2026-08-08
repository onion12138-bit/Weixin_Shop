-- 顾客账户中心：资料、地址和优惠权益。

create table customer_profile (
    customer_identity_id bigint primary key,
    nickname varchar(40) not null,
    avatar_asset_id bigint null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_customer_profile_identity
        foreign key (customer_identity_id) references customer_identity(id),
    constraint fk_customer_profile_avatar
        foreign key (avatar_asset_id) references asset_file(id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table customer_address (
    id bigint primary key auto_increment,
    customer_identity_id bigint not null,
    receiver_name varchar(40) not null,
    phone varchar(30) not null,
    region varchar(120) not null,
    detail varchar(240) not null,
    is_default boolean not null default false,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_customer_address_identity
        foreign key (customer_identity_id) references customer_identity(id),
    constraint chk_customer_address_status
        check (status in ('ACTIVE', 'DELETED')),
    index idx_customer_address_owner (customer_identity_id, status, is_default, updated_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table coupon_template (
    id bigint primary key auto_increment,
    code varchar(64) not null unique,
    name varchar(80) not null,
    description varchar(240) not null,
    discount_amount decimal(12,2) not null,
    minimum_spend decimal(12,2) not null default 0,
    valid_days int not null,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_coupon_template_amount
        check (discount_amount > 0 and minimum_spend >= 0),
    constraint chk_coupon_template_days
        check (valid_days between 1 and 3650),
    constraint chk_coupon_template_status
        check (status in ('ACTIVE', 'INACTIVE'))
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table customer_coupon (
    id bigint primary key auto_increment,
    coupon_template_id bigint not null,
    customer_identity_id bigint not null,
    status varchar(20) not null default 'UNUSED',
    issued_at timestamp not null default current_timestamp,
    expires_at timestamp not null,
    used_at timestamp null,
    note varchar(120) null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_customer_coupon_template
        foreign key (coupon_template_id) references coupon_template(id),
    constraint fk_customer_coupon_identity
        foreign key (customer_identity_id) references customer_identity(id),
    constraint chk_customer_coupon_status
        check (status in ('UNUSED', 'USED', 'EXPIRED', 'REVOKED')),
    index idx_customer_coupon_owner (customer_identity_id, status, expires_at),
    index idx_customer_coupon_template (coupon_template_id, issued_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;
