package com.xiaolou.xiaolouainocodebackend;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.xiaolou.xiaolouainocodebackend.mapper")
@ComponentScan("com.xiaolou")
@EnableDubbo
public class AiNoCodeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiNoCodeUserApplication.class, args);
    }
}
