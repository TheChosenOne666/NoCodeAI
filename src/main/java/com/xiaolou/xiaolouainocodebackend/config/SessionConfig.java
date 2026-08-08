package com.xiaolou.xiaolouainocodebackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session Cookie 配置
 *
 * 背景：
 *  - 前端部署在 Cloudflare Pages (xiaolou-nocode.pages.dev)，后端部署在 Railway (nocodeai-production.up.railway.app)，
 *    属于跨站 (cross-site) 架构。
 *  - 默认 Spring Session 通过 yaml 设的 server.servlet.session.cookie.same-site 在 Spring Boot 3.x 不被识别，
 *    导致 SameSite 仍是默认 Lax，Chrome/Edge 会拦截跨站第三方 Cookie，前端表现"登录成功但实际未登录"。
 *
 * 修复：通过 DefaultCookieSerializer 显式设 sameSite=None + useSecureCookie=true（Railway 是 https）。
 *
 * 影响范围：仅 prod profile 生效（@Profile("prod")），本地 dev 同源 http 仍用默认 Lax，避免本地无 https 时 Cookie 直接被丢弃。
 */
@Configuration
@org.springframework.context.annotation.Profile("prod")
public class SessionConfig {

    private static final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    public SessionConfig() {
        log.info("[SessionConfig] init - profile=prod, Cookie SameSite=None + Secure (for xiaolou-nocode.pages.dev cross-site)");
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        // 跨站必须 None，否则 Chrome/Edge 拦截第三方 Cookie
        serializer.setSameSite("None");
        // Railway 是 https，必须 Secure，否则浏览器直接丢弃 None + 非 Secure 的 Cookie
        serializer.setUseSecureCookie(true);
        // 保留原 yaml 里的配置
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setCookieMaxAge(2592000); // 30 天
        serializer.setUseHttpOnlyCookie(true);
        return serializer;
    }
}