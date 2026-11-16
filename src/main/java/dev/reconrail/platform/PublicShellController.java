package dev.reconrail.platform;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only known shell routes forward to HTML. API and unknown paths never do. */
@Controller
public class PublicShellController {
    @GetMapping({"/", "/guide", "/example", "/sign-in", "/cases", "/account", "/closure-status"})
    public String shell() {
        return "forward:/index.html";
    }
}
