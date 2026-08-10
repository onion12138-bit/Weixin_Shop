-- Expand the model-visible catalog context and persist admin-only recommendation traces.
alter table ai_model_config
    add column preference_prompt text null after recommendation_prompt,
    add column soft_preference_enabled boolean not null default true after preference_prompt;

update ai_model_config
set preference_prompt = '优先理解用户明确提出的材质、颜色和标签偏好。逐项查看候选珠子的名称、材质、颜色描述和全部标签；若存在匹配候选，尽量在多个方案中体现，但不作为强制约束。不得虚构候选列表之外的珠子。'
where id = 1;

update ai_model_config
set recommendation_prompt = '根据客户的生日画像与期望，从候选珠子中推荐风格有区分的日常搭配。优先参考客户明确提出的材质、颜色和标签偏好，并说明每种材质的选择理由；星座、生肖和文化寓意仅作搭配参考，不作任何效果承诺。'
where id = 1
  and recommendation_prompt = '根据客户的生日画像与期望，从候选珠子中推荐风格有区分的日常搭配。说明材质选择理由；星座、生肖和文化寓意仅作搭配参考，不作任何效果承诺。';

alter table ai_model_config
    modify preference_prompt text not null;

create table ai_recommendation_log (
    id bigint primary key auto_increment,
    trace_id varchar(64) not null,
    status varchar(24) not null,
    model_name varchar(120) null,
    wish_summary varchar(255) null,
    preference_summary varchar(500) null,
    candidate_count int not null default 0,
    matched_candidate_count int not null default 0,
    matched_plan_count int not null default 0,
    plan_count int not null default 0,
    attempt_count int not null default 0,
    duration_ms bigint null,
    error_code varchar(80) null,
    error_message varchar(500) null,
    request_json longtext null,
    config_snapshot_json longtext null,
    candidates_json longtext null,
    attempts_json longtext null,
    preference_analysis_json longtext null,
    final_response_json longtext null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uk_ai_recommendation_log_trace unique (trace_id),
    constraint chk_ai_recommendation_log_status
        check (status in ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    index idx_ai_recommendation_log_created_at (created_at),
    index idx_ai_recommendation_log_status_created (status, created_at)
);
