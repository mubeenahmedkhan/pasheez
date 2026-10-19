package com.pasheez;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PasheezApplication {

    public static void main(String[] args) {
        SpringApplication.run(PasheezApplication.class, args);
    }

}
