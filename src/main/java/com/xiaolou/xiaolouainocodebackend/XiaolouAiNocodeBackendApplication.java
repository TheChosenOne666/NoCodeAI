package com.xiaolou.xiaolouainocodebackend;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 主类（项目启动入口）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
// todo 如需开启 Redis，须移除 exclude 中的内容
@SpringBootApplication
@MapperScan("com.xiaolou.xiaolouainocodebackend.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class XiaolouAiNocodeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaolouAiNocodeBackendApplication.class, args);
    }

}
