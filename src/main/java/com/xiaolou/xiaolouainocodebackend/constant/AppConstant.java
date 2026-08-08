package com.xiaolou.xiaolouainocodebackend.constant;

public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名（对外访问前缀）
     * 优先取环境变量 CODE_DEPLOY_HOST（Railway 可设为 https://<railway域名>/api），
     * 本地未配置时回退到 http://localhost:8123/api
     */
    String CODE_DEPLOY_HOST = System.getenv("CODE_DEPLOY_HOST") != null
            ? System.getenv("CODE_DEPLOY_HOST")
            : "http://localhost:8123/api";

}
