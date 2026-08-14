package com.qrserve.menu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableCaching
@ComponentScan(basePackages = {
    "com.qrserve.menu",
    "com.qrserve.shared.common",
    "com.qrserve.shared.security",
    "com.qrserve.shared.exceptions"
})
@EnableJpaRepositories(basePackages = {"com.qrserve.menu.repository"})
public class MenuServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }
}