package dev.reconrail.access;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import java.time.Instant;
import java.util.List;

public record PendingLogins(List<Entry> requests) {
    public PendingLogins { requests=List.copyOf(requests);if(requests.size()>2)throw SessionEnvelope.invalid(); }
    public record Entry(OAuth2AuthorizationRequest request,Instant createdAt) {}
}
