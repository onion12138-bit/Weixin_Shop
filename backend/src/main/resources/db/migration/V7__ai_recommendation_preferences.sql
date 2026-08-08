-- 管理员可维护的推荐业务提示词与返回套数；模型和安全契约仍由服务端控制。
alter table ai_model_config
    add column recommendation_prompt text null after max_output_tokens,
    add column plan_count int not null default 3 after recommendation_prompt;

update ai_model_config
set recommendation_prompt = '根据客户的生日画像与期望，从候选珠子中推荐风格有区分的日常搭配。说明材质选择理由；星座、生肖和文化寓意仅作搭配参考，不作任何效果承诺。'
where id = 1;

alter table ai_model_config
    modify recommendation_prompt text not null,
    add constraint chk_ai_plan_count check (plan_count between 1 and 5);
