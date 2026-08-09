package com.qrserve.qr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.qrserve.qr",
    "com.qrserve.shared.security",
    "com.qrserve.shared.common",
    "com.qrserve.shared.exceptions"
})
public class QrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(QrServiceApplication.class, args);
    }
}