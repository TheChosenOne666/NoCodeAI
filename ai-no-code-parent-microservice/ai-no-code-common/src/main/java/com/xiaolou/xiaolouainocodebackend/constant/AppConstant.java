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
     * 应用部署域名（可由环境变量覆盖，用于指向 COS 公有读域名）
     */
    String CODE_DEPLOY_HOST = System.getProperty("CODE_DEPLOY_HOST", "http://localhost");

    /**
     * COS 对象键前缀：部署产物（构建后的 dist）
     */
    String CODE_DEPLOY_COS_PREFIX = "code-deploy";

    /**
     * COS 对象键前缀：生成源码
     */
    String CODE_SOURCE_COS_PREFIX = "code-source";

}
