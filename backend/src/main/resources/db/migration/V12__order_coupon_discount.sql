-- 订单优惠券快照与核销状态。
-- coupon_id 为空表示未使用优惠券；唯一约束确保一张顾客券最多绑定一个未删除订单。

alter table customer_order
    add column coupon_id bigint null after cord_fee,
    add column discount_amount decimal(12,2) not null default 0 after coupon_id,
    add constraint fk_customer_order_coupon
        foreign key (coupon_id) references customer_coupon(id),
    add constraint uk_customer_order_coupon unique (coupon_id),
    add constraint chk_customer_order_discount
        check (discount_amount >= 0);
