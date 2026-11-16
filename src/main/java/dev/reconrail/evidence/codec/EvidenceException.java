package dev.reconrail.evidence.codec;

/** Stable bounded failure; rejected source values and parser causes are never exposed. */
public final class EvidenceException extends IllegalArgumentException {
    public EvidenceException() { super("INVALID_EVIDENCE"); }
}
