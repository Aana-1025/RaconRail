package dev.reconrail.access;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@Profile("auth")
class SessionEndpoints {
    @GetMapping("/api/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/api/session") Map<String,Object> session(Authentication authentication){
        var p=(AppPrincipal)authentication.getPrincipal();return Map.of("accountId",p.accountId().toString(),"workspaceId",p.workspaceId().toString(),"sessionGeneration",Long.toString(p.generation()),"expiresAt",(p.originalLogin().plusSeconds(28800).isBefore(p.lastActivity().plusSeconds(1800))?p.originalLogin().plusSeconds(28800):p.lastActivity().plusSeconds(1800)).toString(),"capabilities",java.util.List.of());
    }
}
