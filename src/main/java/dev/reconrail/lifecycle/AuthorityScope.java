package dev.reconrail.lifecycle;

import java.util.Objects;
import java.util.UUID;

/** Immutable authority coordinates. A generation is part of identity, never metadata. */
public record AuthorityScope(
        UUID accountId,
        long accountGeneration,
        Kind kind,
        UUID workspaceId,
        UUID resourceId,
        long resourceGeneration) {
    public enum Kind { ACCOUNT, CASE }

    public AuthorityScope {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resourceId, "resourceId");
        if (accountGeneration < 1 || resourceGeneration < 1) {
            throw new IllegalArgumentException("GENERATION_REQUIRED");
        }
        if (kind == Kind.ACCOUNT) {
            if (workspaceId != null || !accountId.equals(resourceId) || accountGeneration != resourceGeneration) {
                throw new IllegalArgumentException("ACCOUNT_SCOPE_MISMATCH");
            }
        } else if (workspaceId == null) {
            throw new IllegalArgumentException("CASE_WORKSPACE_REQUIRED");
        }
    }

    public static AuthorityScope account(UUID accountId, long generation) {
        return new AuthorityScope(accountId, generation, Kind.ACCOUNT, null, accountId, generation);
    }

    public static AuthorityScope caseScope(UUID accountId, long accountGeneration, UUID workspaceId,
            UUID caseId, long caseGeneration) {
        return new AuthorityScope(accountId, accountGeneration, Kind.CASE, workspaceId, caseId, caseGeneration);
    }
}
