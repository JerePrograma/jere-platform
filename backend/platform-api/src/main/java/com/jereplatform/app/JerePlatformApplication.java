package com.jereplatform.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.jereplatform")
public class JerePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(JerePlatformApplication.class, args);
    }
}
