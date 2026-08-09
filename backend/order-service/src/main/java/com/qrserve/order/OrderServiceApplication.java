package com.qrserve.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.qrserve.order",
    "com.qrserve.shared.common",
    "com.qrserve.shared.security",
    "com.qrserve.shared.exceptions"
})
@EnableJpaRepositories(basePackages = {
    "com.qrserve.order.repository"
})
@EntityScan(basePackages = {
    "com.qrserve.order.entity"
})
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}