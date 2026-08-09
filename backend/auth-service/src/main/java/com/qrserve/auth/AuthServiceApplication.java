package com.qrserve.auth;


import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.qrserve.auth.entity.UserEntity; 
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.security.UserRole;


@SpringBootApplication
@ComponentScan(basePackages = {
    "com.qrserve.auth",
    "com.qrserve.shared.security",
    "com.qrserve.shared.common",
    "com.qrserve.shared.exceptions"
})
@EnableJpaRepositories(basePackages = {
    "com.qrserve.auth.repository"
})
@EntityScan(basePackages = {
    "com.qrserve.auth.entity"
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByEmail("admin@hotel.com")) {
                UserEntity admin = UserEntity.builder()
                        .name("Super Admin")
                        .email("admin@hotel.com")
                        .passwordHash(passwordEncoder.encode("password"))
                        .role(UserRole.SUPER_ADMIN)
                        .enabled(true)
                        .build();

                userRepository.save(admin);
                System.out.println("✅ Default Admin User created: admin@hotel.com / password");
            }
        };
    }
}