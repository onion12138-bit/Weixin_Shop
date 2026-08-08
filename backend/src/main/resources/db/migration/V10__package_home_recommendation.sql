alter table product_package
    add column recommended boolean not null default false after status;

create index idx_package_home_recommendation
    on product_package (recommended, status, updated_at);
