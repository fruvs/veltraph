package dev.veltraph.client.navigation;
/** Narrow refusal from the exact actor's guarded key adapter. Unrelated failures remain errors. */
public final class GroundPublicationRevokedException extends RuntimeException {
    public GroundPublicationRevokedException(String reason){super(reason);}
}
