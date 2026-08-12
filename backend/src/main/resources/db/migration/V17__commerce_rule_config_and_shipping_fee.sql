-- 交易门槛与运费规则由后台统一配置，订单保存创建时的运费快照。

create table commerce_rule_config (
    id bigint primary key,
    minimum_order_amount decimal(12,2) not null,
    free_shipping_threshold decimal(12,2) not null,
    base_shipping_fee decimal(12,2) not null,
    config_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_commerce_rule_amounts check (
        minimum_order_amount >= 0
        and free_shipping_threshold >= 0
        and base_shipping_fee >= 0
    )
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

insert into commerce_rule_config (
    id,
    minimum_order_amount,
    free_shipping_threshold,
    base_shipping_fee,
    config_version
) values (1, 20.00, 60.00, 8.00, 1);

alter table customer_order
    add column shipping_fee decimal(12,2) not null default 0 after discount_amount,
    add constraint chk_customer_order_shipping_fee check (shipping_fee >= 0);
