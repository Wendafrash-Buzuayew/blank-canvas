package com.qrserve.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.qrserve.merchant",
    "com.qrserve.shared.security",
    "com.qrserve.shared.common",
    "com.qrserve.shared.events",
    "com.qrserve.shared.exceptions"
})
@EnableJpaRepositories(basePackages = {"com.qrserve.merchant.repository"})
public class MerchantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}