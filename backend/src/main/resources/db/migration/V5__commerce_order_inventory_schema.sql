-- 库存、订单、模拟支付和履约第一版结构。
-- 当前阶段只实现本地模拟支付，不接真实微信支付；但表结构保留渠道、流水和幂等记录。

create table inventory_balance (
    id bigint primary key auto_increment,
    bead_sku_id bigint not null,
    available_qty int not null,
    reserved_qty int not null,
    sold_qty int not null,
    version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uk_inventory_balance_sku unique (bead_sku_id),
    constraint fk_inventory_balance_sku
        foreign key (bead_sku_id) references bead_sku(id),
    constraint chk_inventory_quantities check (
        available_qty >= 0 and reserved_qty >= 0 and sold_qty >= 0
    ),
    index idx_inventory_alert (available_qty)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table customer_order (
    id bigint primary key auto_increment,
    order_no varchar(40) not null,
    customer_identity_id bigint not null,
    diy_scheme_id bigint not null,
    status varchar(32) not null,
    contact_name varchar(80) not null,
    contact_phone varchar(40) not null,
    address_detail varchar(500) not null,
    scheme_snapshot_json json not null,
    bead_amount decimal(12,2) not null,
    craft_fee decimal(12,2) not null,
    cord_fee decimal(12,2) not null,
    total_amount decimal(12,2) not null,
    paid_at timestamp null,
    cancelled_at timestamp null,
    shipped_at timestamp null,
    completed_at timestamp null,
    version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uk_customer_order_no unique (order_no),
    constraint fk_customer_order_customer
        foreign key (customer_identity_id) references customer_identity(id),
    constraint fk_customer_order_scheme
        foreign key (diy_scheme_id) references diy_scheme(id),
    constraint chk_customer_order_status check (
        status in (
            'PENDING_PAYMENT',
            'PAID',
            'IN_PRODUCTION',
            'SHIPPED',
            'COMPLETED',
            'CANCELLED'
        )
    ),
    constraint chk_customer_order_amounts check (
        bead_amount >= 0
        and craft_fee >= 0
        and cord_fee >= 0
        and total_amount >= 0
    ),
    index idx_customer_order_owner_status_updated (
        customer_identity_id,
        status,
        updated_at
    ),
    index idx_customer_order_status_updated (status, updated_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table order_item (
    id bigint primary key auto_increment,
    order_id bigint not null,
    bead_sku_id bigint not null,
    sku_code varchar(64) not null,
    name varchar(120) not null,
    quantity int not null,
    unit_price decimal(12,2) not null,
    total_price decimal(12,2) not null,
    sort_order int not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_order_item_order
        foreign key (order_id) references customer_order(id),
    constraint fk_order_item_sku
        foreign key (bead_sku_id) references bead_sku(id),
    constraint chk_order_item_quantity check (quantity > 0),
    constraint chk_order_item_amounts check (
        unit_price >= 0 and total_price >= 0
    ),
    index idx_order_item_order_sort (order_id, sort_order),
    index idx_order_item_sku (bead_sku_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table payment_record (
    id bigint primary key auto_increment,
    order_id bigint not null,
    payment_no varchar(48) not null,
    channel varchar(32) not null,
    status varchar(32) not null,
    amount decimal(12,2) not null,
    paid_at timestamp null,
    raw_response_json json null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uk_payment_order unique (order_id),
    constraint uk_payment_no unique (payment_no),
    constraint fk_payment_order
        foreign key (order_id) references customer_order(id),
    constraint chk_payment_channel check (channel in ('MOCK')),
    constraint chk_payment_status check (status in ('INIT', 'SUCCESS', 'FAILED')),
    constraint chk_payment_amount check (amount >= 0),
    index idx_payment_status_created (status, created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table fulfillment_task (
    id bigint primary key auto_increment,
    order_id bigint not null,
    status varchar(32) not null,
    bom_json json not null,
    logistics_company varchar(80) null,
    tracking_no varchar(120) null,
    shipped_at timestamp null,
    completed_at timestamp null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uk_fulfillment_order unique (order_id),
    constraint fk_fulfillment_order
        foreign key (order_id) references customer_order(id),
    constraint chk_fulfillment_status check (
        status in ('PENDING', 'IN_PRODUCTION', 'SHIPPED', 'COMPLETED')
    ),
    index idx_fulfillment_status_updated (status, updated_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table inventory_ledger (
    id bigint primary key auto_increment,
    bead_sku_id bigint not null,
    order_id bigint null,
    change_type varchar(32) not null,
    quantity int not null,
    before_available_qty int not null,
    after_available_qty int not null,
    before_reserved_qty int not null,
    after_reserved_qty int not null,
    before_sold_qty int not null,
    after_sold_qty int not null,
    note varchar(255) null,
    created_at timestamp not null default current_timestamp,
    constraint fk_inventory_ledger_sku
        foreign key (bead_sku_id) references bead_sku(id),
    constraint fk_inventory_ledger_order
        foreign key (order_id) references customer_order(id),
    constraint chk_inventory_ledger_type check (
        change_type in ('INIT', 'RESERVE', 'PAY_CONFIRM', 'RELEASE', 'ADJUST')
    ),
    constraint chk_inventory_ledger_quantity check (quantity > 0),
    constraint chk_inventory_ledger_balances check (
        before_available_qty >= 0
        and after_available_qty >= 0
        and before_reserved_qty >= 0
        and after_reserved_qty >= 0
        and before_sold_qty >= 0
        and after_sold_qty >= 0
    ),
    index idx_inventory_ledger_sku_created (bead_sku_id, created_at),
    index idx_inventory_ledger_order (order_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

insert into inventory_balance (
    bead_sku_id,
    available_qty,
    reserved_qty,
    sold_qty,
    version
) values
    (2001, 200, 0, 0, 0),
    (2002, 200, 0, 0, 0),
    (2003, 200, 0, 0, 0),
    (2004, 200, 0, 0, 0);

insert into inventory_ledger (
    bead_sku_id,
    order_id,
    change_type,
    quantity,
    before_available_qty,
    after_available_qty,
    before_reserved_qty,
    after_reserved_qty,
    before_sold_qty,
    after_sold_qty,
    note
) values
    (2001, null, 'INIT', 200, 0, 200, 0, 0, 0, 0, '演示库存初始化'),
    (2002, null, 'INIT', 200, 0, 200, 0, 0, 0, 0, '演示库存初始化'),
    (2003, null, 'INIT', 200, 0, 200, 0, 0, 0, 0, '演示库存初始化'),
    (2004, null, 'INIT', 200, 0, 200, 0, 0, 0, 0, '演示库存初始化');
