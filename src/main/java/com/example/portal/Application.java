package com.example.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.portal", "com.example.workflow"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(com.example.portal.Application.class, args);
    }
}

