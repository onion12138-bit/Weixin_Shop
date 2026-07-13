-- 单例 AI 模型配置。API Key 只保存 AES-GCM 密文，主密钥来自运行环境。
create table ai_model_config (
    id bigint primary key,
    provider varchar(32) not null,
    base_url varchar(255) not null,
    model_name varchar(120) not null,
    encrypted_api_key text null,
    enabled boolean not null,
    timeout_seconds int not null,
    max_output_tokens int not null,
    version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_ai_model_provider check (provider = 'DEEPSEEK'),
    constraint chk_ai_model_timeout check (timeout_seconds > 0),
    constraint chk_ai_model_max_output_tokens check (max_output_tokens > 0)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

insert into ai_model_config (
    id,
    provider,
    base_url,
    model_name,
    encrypted_api_key,
    enabled,
    timeout_seconds,
    max_output_tokens,
    version
) values (
    1,
    'DEEPSEEK',
    'https://api.deepseek.com',
    'deepseek-v4-flash',
    null,
    false,
    30,
    2048,
    0
);
