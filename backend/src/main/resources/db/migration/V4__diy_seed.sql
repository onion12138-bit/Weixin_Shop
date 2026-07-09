-- DIY 演示默认配置。
-- 所有值都通过后台配置页面可调整，这里只保证系统首次启动即可完成闭环演示。

insert into diy_rule_config (
    id,
    min_wrist_mm,
    max_wrist_mm,
    snug_allowance_mm,
    standard_allowance_mm,
    loose_allowance_mm,
    tolerance_mm,
    max_bead_count,
    config_version
) values (1, 120.00, 250.00, 5.00, 10.00, 15.00, 4.00, 60, 1);

insert into craft_fee_rule (
    id,
    min_bead_count,
    max_bead_count,
    fee,
    status,
    sort_order,
    rule_version
) values (5001, 1, 60, 10.00, 'ENABLED', 10, 1);

insert into cord_option (
    id,
    code,
    name,
    color_hex,
    extra_fee,
    status,
    sort_order,
    option_version
) values
    (6001, 'ELASTIC-CLEAR', '透明弹力绳', '#E8E4DC', 0.00, 'ENABLED', 10, 1),
    (6002, 'ELASTIC-BLACK', '黑色弹力绳', '#272523', 0.00, 'ENABLED', 20, 1),
    (6003, 'ELASTIC-BROWN', '棕色弹力绳', '#72513C', 0.00, 'ENABLED', 30, 1);
