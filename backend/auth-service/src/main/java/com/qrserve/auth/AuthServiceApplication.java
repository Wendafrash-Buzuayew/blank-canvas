package com.qrserve.auth;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
public class AuthServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    /**
     * Local-development convenience only: seeds a SUPER_ADMIN with a well-known
     * password. Never registered under the {@code prod} profile, which every
     * deployed environment (k8s base, docker-compose) runs with - a default
     * super-admin there is a full platform takeover.
     */
    @Bean
    @Profile("!prod")
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
                log.warn("Seeded default dev SUPER_ADMIN admin@hotel.com (non-prod profile only)");
            }
        };
    }
}
