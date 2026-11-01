-- 旧券没有历史规则版本，只能明确标记为按迁移当时模板回填。
alter table customer_coupon
    add column coupon_code varchar(64) null,
    add column coupon_name varchar(80) null,
    add column coupon_description varchar(240) null,
    add column discount_amount decimal(12,2) null,
    add column minimum_spend decimal(12,2) null,
    add column snapshot_source varchar(40) null,
    add column grant_batch_id varchar(80) collate utf8mb4_bin null;

update customer_coupon c join coupon_template t on t.id = c.coupon_template_id
set c.coupon_code=t.code, c.coupon_name=t.name, c.coupon_description=t.description,
    c.discount_amount=t.discount_amount, c.minimum_spend=t.minimum_spend,
    c.snapshot_source='MIGRATED_CURRENT_TEMPLATE';

alter table customer_coupon
    modify coupon_code varchar(64) not null,
    modify coupon_name varchar(80) not null,
    modify coupon_description varchar(240) not null,
    modify discount_amount decimal(12,2) not null,
    modify minimum_spend decimal(12,2) not null,
    modify snapshot_source varchar(40) not null,
    add constraint chk_coupon_snapshot_amount check (discount_amount > 0 and minimum_spend >= 0),
    add unique key uk_coupon_grant_recipient (grant_batch_id, customer_identity_id);

-- 批次结果和实际发券同事务提交；同一批次重试不重新查询全量客户。
create table coupon_grant_batch (
    batch_id varchar(80) collate utf8mb4_bin primary key,
    request_hash char(64) not null,
    requested_count int null,
    granted_count int null,
    skipped_count int null,
    coupon_id bigint null,
    created_at timestamp not null default current_timestamp
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
