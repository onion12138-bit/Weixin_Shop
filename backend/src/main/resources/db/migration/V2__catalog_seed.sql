-- 可重复演示所需的最小商品集。
-- 固定主键让接口示例、小程序截图和自动化测试拥有稳定引用。

insert into category (id, name, slug, sort_order, status)
values
    (1001, '天然水晶', 'natural-crystal', 10, 'ON_SALE'),
    (1002, '木质与菩提', 'wood-and-bodhi', 20, 'ON_SALE');

insert into bead_sku (
    id,
    category_id,
    sku_code,
    name,
    material,
    color,
    diameter_mm,
    sale_price,
    cost_price,
    stock_alert_threshold,
    cover_asset_id,
    tags_json,
    status
)
values
    (
        2001, 1001, 'BEAD-CRYSTAL-001', '白水晶 8mm', '天然水晶', '透明',
        8.00, 12.80, 5.20, 20, null, json_array('净化', '日常'), 'ON_SALE'
    ),
    (
        2002, 1001, 'BEAD-AMETHYST-001', '紫水晶 8mm', '天然水晶', '紫色',
        8.00, 16.80, 7.40, 20, null, json_array('专注', '学业'), 'ON_SALE'
    ),
    (
        2003, 1001, 'BEAD-ROSE-QUARTZ-001', '粉水晶 8mm', '天然水晶', '粉色',
        8.00, 14.80, 6.10, 20, null, json_array('温柔', '人缘'), 'ON_SALE'
    ),
    (
        2004, 1002, 'BEAD-SANDALWOOD-001', '老山檀 8mm', '檀木', '棕色',
        8.00, 18.80, 8.50, 15, null, json_array('沉静', '东方'), 'ON_SALE'
    );

insert into product_package (
    id,
    package_code,
    name,
    description,
    sale_price,
    cover_asset_id,
    status
)
values (
    3001,
    'PACKAGE-CALM-001',
    '澄心日常手串',
    '白水晶、紫水晶与老山檀组成的日常入门套餐。',
    168.00,
    null,
    'ON_SALE'
);

insert into package_item (
    id,
    package_id,
    bead_sku_id,
    quantity,
    sort_order
)
values
    (4001, 3001, 2001, 8, 10),
    (4002, 3001, 2002, 4, 20),
    (4003, 3001, 2004, 6, 30);
