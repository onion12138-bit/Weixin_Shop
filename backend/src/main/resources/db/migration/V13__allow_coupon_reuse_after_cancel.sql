-- 待支付订单取消后会退回优惠券，因此不能用订单历史上的 coupon_id 唯一约束阻止再次使用。
-- 并发防重由创建订单事务中的顾客券行锁和 USED 状态保证。

-- 外键本身仍需要普通索引；先补一个非唯一索引，再移除 V12 中的唯一索引。
alter table customer_order
    add index idx_customer_order_coupon_id (coupon_id);

alter table customer_order
    drop index uk_customer_order_coupon;
