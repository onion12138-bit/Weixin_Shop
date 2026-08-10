update diy_visual_config
set bead_shadow_opacity = 0.100,
    config_version = config_version + 1,
    updated_by = 'migration-v16'
where id = 1;
