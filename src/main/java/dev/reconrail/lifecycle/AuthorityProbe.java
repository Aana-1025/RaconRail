package dev.reconrail.lifecycle;

import java.io.IOException;

/** Revalidates durable account/resource state immediately before one response chunk. */
@FunctionalInterface
public interface AuthorityProbe {
    void requireCurrent(AuthorityScope scope) throws IOException;
}
