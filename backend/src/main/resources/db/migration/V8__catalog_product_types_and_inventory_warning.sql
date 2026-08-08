-- 商品分类区分珠子、绳类和其他饰品；只有珠子要求珠径。
-- 库存数量沿用 inventory_balance，预警阈值沿用 bead_sku.stock_alert_threshold。

alter table category
    add column product_type varchar(32) not null default 'BEAD' after slug;

alter table category
    add constraint chk_category_product_type
        check (product_type in ('BEAD', 'CORD', 'ACCESSORY'));

alter table bead_sku
    drop check chk_bead_diameter_positive;

alter table bead_sku
    modify column diameter_mm decimal(5,2) null;

alter table bead_sku
    add constraint chk_catalog_diameter_positive
        check (diameter_mm is null or diameter_mm > 0);

insert into category (name, slug, product_type, sort_order, status)
select '绳类', 'cord', 'CORD', 30, 'ON_SALE'
where not exists (
    select 1 from category where slug = 'cord'
);

insert into category (name, slug, product_type, sort_order, status)
select '其他饰品', 'accessory', 'ACCESSORY', 40, 'ON_SALE'
where not exists (
    select 1 from category where slug = 'accessory'
);
