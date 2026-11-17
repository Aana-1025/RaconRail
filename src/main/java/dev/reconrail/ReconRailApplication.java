package dev.reconrail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName="org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class ReconRailApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReconRailApplication.class, args);
    }
}
