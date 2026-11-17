package dev.reconrail.access;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("!auth")
class PublicShellSecurity {
    @Bean SecurityFilterChain shellSecurity(HttpSecurity http)throws Exception{
        return http.authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
    }
}
