package com.trimly.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrimlyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrimlyBackendApplication.class, args);
    }
}
