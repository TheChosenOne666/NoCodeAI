-- 应用部署静态资源表：将精品案例作品（已部署的前端产物）存入 MySQL，
-- 用户在无本地磁盘的生产环境通过 /api/static/{deployKey}/{path} 直接查库返回。
CREATE TABLE IF NOT EXISTS app_deploy_asset (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    deploy_key  VARCHAR(64)  NOT NULL COMMENT '部署标识（对应 app.deployKey）',
    file_path   VARCHAR(512) NOT NULL COMMENT '文件相对路径，如 index.html / assets/xxx.js',
    content_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream' COMMENT 'MIME 类型',
    file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
    content     LONGBLOB     NOT NULL COMMENT '文件二进制内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_deploy_path (deploy_key, file_path),
    KEY idx_deploy_key (deploy_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用部署静态资源表';
