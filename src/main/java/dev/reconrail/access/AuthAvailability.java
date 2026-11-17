package dev.reconrail.access;

import org.springframework.web.bind.annotation.*;
import org.springframework.core.env.Environment;
import java.util.*;

@RestController
class AuthAvailability {
    private final Environment environment;
    AuthAvailability(Environment environment){this.environment=environment;}
    @GetMapping("/api/auth/availability") Map<String,Boolean> availability(){return Map.of("enabled",Arrays.asList(environment.getActiveProfiles()).contains("auth"));}
}
