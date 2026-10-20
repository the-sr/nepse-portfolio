package com.nepse.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NepsePortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(NepsePortfolioApplication.class, args);
    }
}
