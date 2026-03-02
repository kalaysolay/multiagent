package com.example.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ComponentScan(basePackages = {"com.example.portal", "com.example.workflow", "com.example.mcp"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(com.example.portal.Application.class, args);
    }
}

