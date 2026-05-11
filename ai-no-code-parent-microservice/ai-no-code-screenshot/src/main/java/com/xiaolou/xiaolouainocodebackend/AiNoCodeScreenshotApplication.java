package com.xiaolou.xiaolouainocodebackend;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class AiNoCodeScreenshotApplication {
    public static void main(String[] args){
        SpringApplication.run(AiNoCodeScreenshotApplication.class, args);
    }
}
