-- 统一订单主状态：支付成功直接进入制作中，完成制作后进入待发货。

update customer_order
set status = 'IN_PRODUCTION'
where status = 'PAID';

alter table customer_order
    drop check chk_customer_order_status;

alter table customer_order
    add column order_note varchar(500) null after address_detail,
    add constraint chk_customer_order_status check (
        status in (
            'PENDING_PAYMENT',
            'IN_PRODUCTION',
            'PENDING_SHIPMENT',
            'SHIPPED',
            'COMPLETED',
            'CANCELLED',
            'AFTER_SALE',
            'REFUNDED'
        )
    ),
    add index idx_customer_order_contact_created (
        contact_name,
        contact_phone,
        created_at
    );

alter table fulfillment_task
    add column production_started_at timestamp null after tracking_no,
    add column production_completed_at timestamp null after production_started_at;

update fulfillment_task fulfillment
join customer_order orders on orders.id = fulfillment.order_id
set fulfillment.status = 'IN_PRODUCTION',
    fulfillment.production_started_at = orders.paid_at
where fulfillment.status = 'PENDING';

alter table fulfillment_task
    drop check chk_fulfillment_status;

alter table fulfillment_task
    add constraint chk_fulfillment_status check (
        status in (
            'IN_PRODUCTION',
            'PENDING_SHIPMENT',
            'SHIPPED',
            'COMPLETED'
        )
    );

create table order_operation_log (
    id bigint primary key auto_increment,
    order_id bigint not null,
    action_code varchar(48) not null,
    operator_type varchar(24) not null,
    operator_name varchar(80) not null,
    content varchar(255) not null,
    occurred_at timestamp not null default current_timestamp,
    created_at timestamp not null default current_timestamp,
    constraint fk_order_operation_order
        foreign key (order_id) references customer_order(id),
    constraint chk_order_operation_operator check (
        operator_type in ('USER', 'SYSTEM', 'ADMIN')
    ),
    index idx_order_operation_order_time (order_id, occurred_at, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 为升级前订单补充最基础的历史轨迹，新动作由应用服务实时写入。
insert into order_operation_log (
    order_id, action_code, operator_type, operator_name, content, occurred_at
)
select id, 'ORDER_CREATED', 'USER', '用户', '提交订单', created_at
from customer_order;

insert into order_operation_log (
    order_id, action_code, operator_type, operator_name, content, occurred_at
)
select id, 'PAYMENT_SUCCEEDED', 'SYSTEM', '系统', '微信支付成功', paid_at
from customer_order
where paid_at is not null;

insert into order_operation_log (
    order_id, action_code, operator_type, operator_name, content, occurred_at
)
select id, 'ORDER_SHIPPED', 'ADMIN', '后台管理员', '订单发货', shipped_at
from customer_order
where shipped_at is not null;

insert into order_operation_log (
    order_id, action_code, operator_type, operator_name, content, occurred_at
)
select id, 'ORDER_COMPLETED', 'SYSTEM', '系统', '订单完成', completed_at
from customer_order
where completed_at is not null;

insert into order_operation_log (
    order_id, action_code, operator_type, operator_name, content, occurred_at
)
select id, 'ORDER_CANCELLED', 'USER', '用户', '取消订单', cancelled_at
from customer_order
where cancelled_at is not null;
