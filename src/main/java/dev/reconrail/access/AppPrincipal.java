package dev.reconrail.access;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/** Internal authority only; no provider identity, email, claims or tokens. */
public record AppPrincipal(UUID accountId, UUID workspaceId, long generation,
                           Instant originalLogin, Instant lastActivity, Instant authTime) implements Principal {
    public AppPrincipal {
        if(accountId==null||workspaceId==null||generation<=0||originalLogin==null||lastActivity==null)throw new IllegalArgumentException("INVALID_PRINCIPAL");
    }
    @Override public String getName(){return accountId+":"+generation;}
    public boolean expired(Instant now){return now.isBefore(originalLogin)||now.isBefore(lastActivity)||!now.isBefore(originalLogin.plusSeconds(28800))||!now.isBefore(lastActivity.plusSeconds(1800));}
}
