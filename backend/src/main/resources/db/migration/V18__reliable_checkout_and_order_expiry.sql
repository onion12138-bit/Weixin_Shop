-- 提交令牌按顾客隔离；历史订单保留空令牌。
alter table customer_order
    add column checkout_token varchar(80) character set ascii collate ascii_bin null,
    add column checkout_request_hash char(64) character set ascii collate ascii_bin null,
    add column expires_at timestamp(3) null,
    add column closed_reason varchar(40) null,
    add constraint uk_customer_order_checkout unique (customer_identity_id, checkout_token),
    add index idx_customer_order_expiry (status, expires_at, id);

-- 历史待支付订单获得从迁移时开始的完整 30 分钟宽限期。
update customer_order set expires_at = date_add(current_timestamp(3), interval 30 minute)
where status = 'PENDING_PAYMENT';
