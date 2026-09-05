package com.qrserve.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.qrserve.backoffice",
    "com.qrserve.shared.common",
    "com.qrserve.shared.security",
    "com.qrserve.shared.exceptions"
})
public class BackOfficeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackOfficeServiceApplication.class, args);
    }
}
