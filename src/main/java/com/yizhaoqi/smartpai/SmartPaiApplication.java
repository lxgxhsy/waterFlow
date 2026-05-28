package com.yizhaoqi.smartpai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartPaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPaiApplication.class, args);
    }

}
